package org.com.bloodpalace.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.com.bloodpalace.config.RoomConfig;
import org.com.bloodpalace.util.RoomEditor;

public class RoomCoreEntity extends Entity {

    private static final EntityDataAccessor<String> ROOM_ID =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ROOM_NAME =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MIN_X =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MIN_Y =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MIN_Z =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX_X =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX_Y =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX_Z =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TEMPORARY =
        SynchedEntityData.defineId(RoomCoreEntity.class, EntityDataSerializers.BOOLEAN);

    public RoomCoreEntity(EntityType<? extends RoomCoreEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        noCulling = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ROOM_ID, "");
        entityData.define(ROOM_NAME, "");
        entityData.define(MIN_X, 0);
        entityData.define(MIN_Y, 0);
        entityData.define(MIN_Z, 0);
        entityData.define(MAX_X, 0);
        entityData.define(MAX_Y, 0);
        entityData.define(MAX_Z, 0);
        entityData.define(TEMPORARY, false);
    }

    public void setRoom(RoomConfig.Room room, boolean temporary) {
        String name = room.name == null || room.name.isBlank() ? room.id : room.name;
        entityData.set(ROOM_ID, room.id);
        entityData.set(ROOM_NAME, name);
        entityData.set(MIN_X, room.min.x);
        entityData.set(MIN_Y, room.min.y);
        entityData.set(MIN_Z, room.min.z);
        entityData.set(MAX_X, room.max.x);
        entityData.set(MAX_Y, room.max.y);
        entityData.set(MAX_Z, room.max.z);
        entityData.set(TEMPORARY, temporary);
        setCustomName(Component.literal(name));
        setCustomNameVisible(false);
        moveToCenter();
    }

    public String getRoomId() {
        return entityData.get(ROOM_ID);
    }

    public String getRoomName() {
        String name = entityData.get(ROOM_NAME);
        return name.isBlank() ? getRoomId() : name;
    }

    public boolean isTemporary() {
        return entityData.get(TEMPORARY);
    }

    public RoomConfig.Room toRoom() {
        return new RoomConfig.Room(
            getRoomId(),
            getRoomName(),
            new RoomConfig.Pos(entityData.get(MIN_X), entityData.get(MIN_Y), entityData.get(MIN_Z)),
            new RoomConfig.Pos(entityData.get(MAX_X), entityData.get(MAX_Y), entityData.get(MAX_Z)));
    }

    public AABB roomBounds() {
        return new AABB(
            entityData.get(MIN_X),
            entityData.get(MIN_Y),
            entityData.get(MIN_Z),
            entityData.get(MAX_X) + 1.0,
            entityData.get(MAX_Y) + 1.0,
            entityData.get(MAX_Z) + 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!player.isCreative() || !serverPlayer.hasPermissions(2)) {
            return InteractionResult.PASS;
        }
        return RoomEditor.openCoreEditor(serverPlayer, this) > 0
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }

    @Override
    public boolean shouldBeSaved() {
        return !isTemporary() && super.shouldBeSaved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        RoomConfig.Room room = new RoomConfig.Room(
            tag.getString("RoomId"),
            tag.getString("RoomName"),
            new RoomConfig.Pos(tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ")),
            new RoomConfig.Pos(tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ")));
        setRoom(room, tag.getBoolean("Temporary"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("RoomId", getRoomId());
        tag.putString("RoomName", getRoomName());
        tag.putInt("MinX", entityData.get(MIN_X));
        tag.putInt("MinY", entityData.get(MIN_Y));
        tag.putInt("MinZ", entityData.get(MIN_Z));
        tag.putInt("MaxX", entityData.get(MAX_X));
        tag.putInt("MaxY", entityData.get(MAX_Y));
        tag.putInt("MaxZ", entityData.get(MAX_Z));
        tag.putBoolean("Temporary", isTemporary());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private void moveToCenter() {
        AABB bounds = roomBounds();
        setPos(bounds.getCenter().x, bounds.getCenter().y, bounds.getCenter().z);
    }
}
