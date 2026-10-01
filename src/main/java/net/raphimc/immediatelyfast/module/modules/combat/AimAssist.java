package net.raphimc.immediatelyfast.module.modules.combat;

import net.raphimc.immediatelyfast.event.events.HudListener;
import net.raphimc.immediatelyfast.event.events.MouseMoveListener;
import net.raphimc.immediatelyfast.module.Category;
import net.raphimc.immediatelyfast.module.Module;
import net.raphimc.immediatelyfast.module.setting.BooleanSetting;
import net.raphimc.immediatelyfast.module.setting.MinMaxSetting;
import net.raphimc.immediatelyfast.module.setting.ModeSetting;
import net.raphimc.immediatelyfast.module.setting.NumberSetting;
import net.raphimc.immediatelyfast.utils.*;
import net.raphimc.immediatelyfast.utils.rotation.Rotation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class AimAssist extends Module implements HudListener, MouseMoveListener {
	private final BooleanSetting stickyAim = new BooleanSetting(EncryptedString.of("Sticky Aim"), false)
			.setDescription(EncryptedString.of("Aims at the last attacked player"));

	private final BooleanSetting onlyWeapon = new BooleanSetting(EncryptedString.of("Only Weapon"), true);

	private final BooleanSetting onLeftClick = new BooleanSetting(EncryptedString.of("On Left Click"), false)
			.setDescription(EncryptedString.of("Only gets triggered if holding down left click"));
	private final ModeSetting<AimMode> aimAt = new ModeSetting<>(EncryptedString.of("Aim At"), AimMode.Head, AimMode.class);

	private final BooleanSetting stopAtTargetVertical = new BooleanSetting(EncryptedString.of("Stop at Target Vert"), true)
			.setDescription(EncryptedString.of("Stops vertically assisting if already aiming at the entity, helps bypass anti-cheat"));

	private final BooleanSetting stopAtTargetHorizontal = new BooleanSetting(EncryptedString.of("Stop at Target Horiz"), false)
			.setDescription(EncryptedString.of("Stops horizontally assisting if already aiming at the entity, helps bypass anti-cheat"));

	private final NumberSetting radius = new NumberSetting(EncryptedString.of("Radius"), 0.1, 12, 5, 0.1);

	private final BooleanSetting seeOnly = new BooleanSetting(EncryptedString.of("Visible Only"), true);
	private final BooleanSetting ignoreNoHP = new BooleanSetting(EncryptedString.of("Ignore 0 HP"), false)
			.setDescription(EncryptedString.of("Stops assisting if target entity has 0 HP (is dead)"));
	private final BooleanSetting lookAtNearest = new BooleanSetting(EncryptedString.of("Look at Nearest"), false);

	private final NumberSetting fov = new NumberSetting(EncryptedString.of("FOV"), 1, 360, 180, 1);

	private final MinMaxSetting pitchSpeed = new MinMaxSetting(EncryptedString.of("Vertical Speed"), 0, 25, 0.1, 2, 5);
	private final MinMaxSetting yawSpeed = new MinMaxSetting(EncryptedString.of("Horizontal Speed"), 0, 25, 0.1, 2, 5);

	private final NumberSetting speedChange = new NumberSetting(EncryptedString.of("Speed Delay"), 0, 2000, 250, 10)
			.setDescription(EncryptedString.of("Time in milliseconds to wait after resetting random speed"));

	private final NumberSetting randomization = new NumberSetting(EncryptedString.of("Chance"), 0, 100, 50, 1);

	private final BooleanSetting yawAssist = new BooleanSetting(EncryptedString.of("Horizontal"), true);
	private final BooleanSetting pitchAssist = new BooleanSetting(EncryptedString.of("Vertical"), true);

	private final NumberSetting waitFor = new NumberSetting(EncryptedString.of("Wait on Move"), 0, 2000, 0, 10)
			.setDescription(EncryptedString.of("After you move your mouse aim assist will stop working for the selected amount of time"));

	private final ModeSetting<LerpMode> lerp = new ModeSetting<>(EncryptedString.of("Lerp"), LerpMode.Normal, LerpMode.class)
			.setDescription(EncryptedString.of("Linear interpolation to use to rotate"));

	private final ModeSetting<PosMode> posMode = new ModeSetting<>(EncryptedString.of("Pos mode"), PosMode.Normal, PosMode.class)
			.setDescription(EncryptedString.of("Precision of the target position"));

	private final TimerUtils timer = new TimerUtils();
	private final TimerUtils resetSpeed = new TimerUtils();
	private boolean move;
	private float currentPitchSpeed, currentYawSpeed;
	private float yawAccumulator = 0.0f;
	private float pitchAccumulator = 0.0f;

	@SuppressWarnings("unused")
	public enum PosMode {
		Normal, Lerped
	}

	public enum AimMode {
		Head, Chest, Legs
	}

	public enum LerpMode {
		Normal, Smoothstep, Curve
	}

	public AimAssist() {
		super(EncryptedString.of("Aim Assist"),
				EncryptedString.of("Automatically aims at players for you"),
				-1,
				Category.COMBAT);

		addSettings(stickyAim, onlyWeapon, onLeftClick, aimAt, stopAtTargetVertical, stopAtTargetHorizontal, radius, seeOnly, ignoreNoHP, lookAtNearest, fov, pitchSpeed, yawSpeed, speedChange, randomization, yawAssist, pitchAssist, waitFor, lerp, posMode);
	}

	@Override
	public void onEnable() {
		move = true;
		currentPitchSpeed = pitchSpeed.getRandomValueFloat();
		currentYawSpeed = yawSpeed.getRandomValueFloat();
		yawAccumulator = 0.0f;
		pitchAccumulator = 0.0f;

		eventManager.add(HudListener.class, this);
		eventManager.add(MouseMoveListener.class, this);

		timer.reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(MouseMoveListener.class, this);
		yawAccumulator = 0.0f;
		pitchAccumulator = 0.0f;
		super.onDisable();
	}

	private float getSensStep() {
		if (mc.options == null) return 0.1f;
		float sens = mc.options.getMouseSensitivity().getValue().floatValue();
		float f = sens * 0.6f + 0.2f;
		return f * f * f * 1.2f;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (timer.delay(waitFor.getValueFloat()) && !move) {
			move = true;
			timer.reset();
		}

		if (mc.player == null || mc.currentScreen != null)
			return;

		if (onlyWeapon.getValue() && !(mc.player.getMainHandStack().getItem() instanceof SwordItem || mc.player.getMainHandStack().getItem() instanceof AxeItem))
			return;

		if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
			return;

		PlayerEntity target = WorldUtils.findNearestPlayer(mc.player, radius.getValueFloat(), seeOnly.getValue(), true);
		if (stickyAim.getValue() && mc.player.getAttacking() instanceof PlayerEntity player && player.distanceTo(mc.player) < radius.getValue())
			target = player;

		if (target == null || (target.getHealth() <= 0.0F && ignoreNoHP.getValue()))
			return;

		if (resetSpeed.delay(speedChange.getValueFloat())) {
			currentPitchSpeed = pitchSpeed.getRandomValueFloat();
			currentYawSpeed = yawSpeed.getRandomValueFloat();
			resetSpeed.reset();
		}

		Vec3d targetPos = posMode.isMode(PosMode.Normal) ? target.getPos() : target.getLerpedPos(mc.getRenderTickCounter().getTickProgress(true));

		if (aimAt.isMode(AimMode.Chest))
			targetPos = targetPos.add(0, -0.5, 0);
		else if (aimAt.isMode(AimMode.Legs))
			targetPos = targetPos.add(0, -1.2, 0);

		if (lookAtNearest.getValue()) {
			double offsetX = mc.player.getX() - target.getX() > 0 ? 0.29 : -0.29;
			double offsetZ = mc.player.getZ() - target.getZ() > 0 ? 0.29 : -0.29;
			targetPos = targetPos.add(offsetX, 0, offsetZ);
		}

		Rotation rotation = RotationUtils.getDirection(mc.player, targetPos);

		double angleToRotation = RotationUtils.getAngleToRotation(rotation);
		if (angleToRotation > (double) fov.getValueInt() / 2)
			return;

		float frameFactor = Math.min(2.0f, Math.max(0.1f, mc.getLastFrameDuration()));
		float yawStrength = (currentYawSpeed / 50.0f) * frameFactor;
		float pitchStrength = (currentPitchSpeed / 50.0f) * frameFactor;

		float playerYaw = mc.player.getYaw();
		float playerPitch = mc.player.getPitch();

		float targetYaw = playerYaw;
		float targetPitch = playerPitch;

		if (lerp.isMode(LerpMode.Smoothstep)) {
			targetYaw = (float) smoothStepLerp(yawStrength, playerYaw, (float) rotation.yaw());
			targetPitch = (float) smoothStepLerp(pitchStrength, playerPitch, (float) rotation.pitch());
		} else if (lerp.isMode(LerpMode.Normal)) {
			targetYaw = lerp(yawStrength, playerYaw, (float) rotation.yaw());
			targetPitch = lerp(pitchStrength, playerPitch, (float) rotation.pitch());
		} else if (lerp.isMode(LerpMode.Curve)) {
			targetYaw = (float) curveLerp(yawStrength, playerYaw, rotation.yaw());
			targetPitch = (float) curveLerp(pitchStrength, playerPitch, rotation.pitch());
		}

		if (MathUtils.randomInt(1, 100) <= randomization.getValueInt() && move) {
			float step = getSensStep();
			float deltaYaw = MathHelper.wrapDegrees(targetYaw - playerYaw);
			float deltaPitch = targetPitch - playerPitch;

			yawAccumulator += deltaYaw;
			pitchAccumulator += deltaPitch;

			float gcdYaw = (int) (yawAccumulator / step) * step;
			float gcdPitch = (int) (pitchAccumulator / step) * step;

			if (yawAssist.getValue() && Math.abs(gcdYaw) > 0.0f) {
				boolean skipYaw = stopAtTargetHorizontal.getValue() && WorldUtils.getHitResult(radius.getValue()) instanceof EntityHitResult hitResult && hitResult.getEntity() == target;
				if (!skipYaw) {
					mc.player.setYaw(MathHelper.wrapDegrees(playerYaw + gcdYaw));
					yawAccumulator -= gcdYaw;
				}
			}

			if (pitchAssist.getValue() && Math.abs(gcdPitch) > 0.0f) {
				boolean skipPitch = stopAtTargetVertical.getValue() && WorldUtils.getHitResult(radius.getValue()) instanceof EntityHitResult hitResult && hitResult.getEntity() == target;
				if (!skipPitch) {
					float nextPitch = Math.max(-90.0f, Math.min(90.0f, playerPitch + gcdPitch));
					mc.player.setPitch(nextPitch);
					pitchAccumulator -= gcdPitch;
				}
			}
		}
	}

	public float lerp(float delta, float start, float end) {
		return start + (MathHelper.wrapDegrees(end - start) * delta);
	}

	public double smoothStepLerp(double delta, double start, double end) {
		delta = Math.max(0, Math.min(1, delta));
		double t = delta * delta * (3 - 2 * delta);
		return start + MathHelper.wrapDegrees((float) (end - start)) * t;
	}

	public double curveLerp(double delta, double start, double end) {
		delta = Math.max(0.0, Math.min(1.0, delta));
		double base = (1.0 - Math.cos(Math.PI * delta)) / 2.0;
		double arc = 0.04 * Math.sin(Math.PI * Math.pow(delta, 1.15));
		double factor = Math.min(1.0, Math.max(0.0, base + arc));
		double noise = ThreadLocalRandom.current().nextGaussian() * 0.02;
		return start + (MathHelper.wrapDegrees((float) (end - start)) * factor) + noise;
	}

	@Override
	public void onMouseMove(MouseMoveEvent event) {
		move = false;
		timer.reset();
	}
}
