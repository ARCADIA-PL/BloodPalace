package org.com.bloodpalace.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class TeleportAnchorEntity extends Entity {

    private static final EntityDataAccessor<String> ANCHOR_NAME =
        SynchedEntityData.defineId(TeleportAnchorEntity.class, EntityDataSerializers.STRING);

    public TeleportAnchorEntity(EntityType<? extends TeleportAnchorEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        noCulling = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ANCHOR_NAME, "");
    }

    public void setAnchorName(String name) {
        String value = name == null || name.isBlank() ? defaultAnchorName() : name;
        entityData.set(ANCHOR_NAME, value);
        setCustomName(Component.literal(value));
        setCustomNameVisible(false);
    }

    public String getAnchorName() {
        String name = entityData.get(ANCHOR_NAME);
        return name.isBlank() ? defaultAnchorName() : name;
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
        if (!level().isClientSide && source.getEntity() instanceof Player player
                && player.isCreative() && player.hasPermissions(2)) {
            discard();
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setAnchorName(tag.getString("AnchorName"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("AnchorName", getAnchorName());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private String defaultAnchorName() {
        return "Anchor " + getBlockX() + " " + getBlockY() + " " + getBlockZ();
    }
}
