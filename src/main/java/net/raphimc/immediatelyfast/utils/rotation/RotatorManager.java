package net.raphimc.immediatelyfast.utils.rotation;

import net.raphimc.immediatelyfast.Argon;
import net.raphimc.immediatelyfast.event.EventManager;
import net.raphimc.immediatelyfast.event.events.*;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;

import java.util.concurrent.ThreadLocalRandom;

import static net.raphimc.immediatelyfast.Argon.mc;

public final class RotatorManager implements PacketSendListener, BlockBreakingListener, ItemUseListener, AttackListener, MovementPacketListener, PacketReceiveListener {
    private boolean enabled;
    private boolean rotateBack;
    private final EventManager eventManager = Argon.INSTANCE.eventManager;

    private Rotation currentRotation;
    private float serverYaw, serverPitch;
    private boolean wasDisabled;

    public RotatorManager() {
        eventManager.add(PacketSendListener.class, this);
        eventManager.add(AttackListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        eventManager.add(MovementPacketListener.class, this);
        eventManager.add(PacketReceiveListener.class, this);
        eventManager.add(BlockBreakingListener.class, this);

        this.enabled = true;
        this.rotateBack = false;
        this.serverYaw = 0;
        this.serverPitch = 0;
    }

    public void shutDown() {
        eventManager.remove(PacketSendListener.class, this);
        eventManager.remove(AttackListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        eventManager.remove(MovementPacketListener.class, this);
        eventManager.remove(PacketReceiveListener.class, this);
        eventManager.remove(BlockBreakingListener.class, this);
    }

    private float getHumanNoise(float intensity) {
        return (float) (ThreadLocalRandom.current().nextGaussian() * intensity);
    }

    private float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private float getSensStep() {
        if (mc.options == null) return 0.1f;
        float sens = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sens * 0.6f + 0.2f;
        return f * f * f * 1.2f;
    }

    private float applyGcd(float delta, float step) {
        return Math.round(delta / step) * step;
    }

    public void setRotation(Rotation rotation) {
        if (mc.player == null || rotation == null) return;
        this.currentRotation = rotation;
    }

    public void setRotation(double yaw, double pitch) {
        setRotation(new Rotation(yaw, pitch));
    }

    @Override
    public void onSendMovementPackets() {
        if (mc.player == null) return;

        if (isEnabled() && currentRotation != null) {
            float playerYaw = mc.player.getYaw();
            float playerPitch = mc.player.getPitch();

            float diffYaw = wrapDegrees((float) currentRotation.yaw() - playerYaw);
            float diffPitch = (float) currentRotation.pitch() - playerPitch;

            float step = getSensStep();

            float speedFactor = (float) ThreadLocalRandom.current().nextDouble(0.35, 0.55);
            float yawNoise = getHumanNoise(0.08f);
            float pitchNoise = getHumanNoise(0.08f);

            float deltaYaw = applyGcd((diffYaw * speedFactor) + yawNoise, step);
            float deltaPitch = applyGcd((diffPitch * speedFactor) + pitchNoise, step);

            float nextYaw = wrapDegrees(playerYaw + deltaYaw);
            float nextPitch = Math.max(-90.0f, Math.min(90.0f, playerPitch + deltaPitch));

            mc.player.setYaw(nextYaw);
            mc.player.setPitch(nextPitch);
            serverYaw = nextYaw;
            serverPitch = nextPitch;
            return;
        }

        if (rotateBack) {
            float playerYaw = mc.player.getYaw();
            float playerPitch = mc.player.getPitch();

            float diffYaw = wrapDegrees(playerYaw - serverYaw);
            float diffPitch = playerPitch - serverPitch;

            if (Math.abs(diffYaw) > 0.8f || Math.abs(diffPitch) > 0.8f) {
                float step = getSensStep();

                float returnSpeed = (float) ThreadLocalRandom.current().nextDouble(0.20, 0.35);
                float deltaYaw = applyGcd(diffYaw * returnSpeed, step);
                float deltaPitch = applyGcd(diffPitch * returnSpeed, step);

                serverYaw = wrapDegrees(serverYaw + deltaYaw);
                serverPitch = Math.max(-90.0f, Math.min(90.0f, serverPitch + deltaPitch));

                mc.player.setYaw(serverYaw);
                mc.player.setPitch(serverPitch);
            } else {
                rotateBack = false;
                currentRotation = null;
            }
        }
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (!isEnabled() && wasDisabled) {
            enabled = true;
            wasDisabled = false;
        }
    }

    @Override
    public void onItemUse(ItemUseEvent event) {
        if (!event.isCancelled() && isEnabled()) {
            enabled = false;
            wasDisabled = true;
        }
    }

    @Override
    public void onBlockBreaking(BlockBreakingEvent event) {
        if (!event.isCancelled() && isEnabled()) {
            enabled = false;
            wasDisabled = true;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            serverYaw = packet.getYaw(serverYaw);
            serverPitch = packet.getPitch(serverPitch);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            float newYaw = packet.change().yaw();
            float newPitch = packet.change().pitch();

            if (packet.relatives().contains(PositionFlag.Y_ROT)) {
                serverYaw = wrapDegrees(serverYaw + newYaw);
            } else {
                serverYaw = wrapDegrees(newYaw);
            }

            if (packet.relatives().contains(PositionFlag.X_ROT)) {
                serverPitch = Math.max(-90.0f, Math.min(90.0f, serverPitch + newPitch));
            } else {
                serverPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        enabled = true;
        rotateBack = false;
    }

    public void disable() {
        if (isEnabled()) {
            enabled = false;
            rotateBack = true;
        }
    }

    public Rotation getServerRotation() {
        return new Rotation(serverYaw, serverPitch);
    }
}
