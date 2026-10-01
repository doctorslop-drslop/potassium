package net.raphimc.immediatelyfast.module.modules.combat;

import net.raphimc.immediatelyfast.Argon;
import net.raphimc.immediatelyfast.event.events.AttackListener;
import net.raphimc.immediatelyfast.event.events.TickListener;
import net.raphimc.immediatelyfast.module.Category;
import net.raphimc.immediatelyfast.module.Module;
import net.raphimc.immediatelyfast.module.modules.client.Friends;
import net.raphimc.immediatelyfast.module.setting.BooleanSetting;
import net.raphimc.immediatelyfast.module.setting.MinMaxSetting;
import net.raphimc.immediatelyfast.module.setting.NumberSetting;
import net.raphimc.immediatelyfast.utils.EncryptedString;
import net.raphimc.immediatelyfast.utils.MouseSimulation;
import net.raphimc.immediatelyfast.utils.TimerUtils;
import net.raphimc.immediatelyfast.utils.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class TriggerBot extends Module implements TickListener, AttackListener {
	private final BooleanSetting inScreen = new BooleanSetting(EncryptedString.of("Work In Screen"), false)
			.setDescription(EncryptedString.of("Will trigger even if youre inside a screen"));
	private final BooleanSetting whileUse = new BooleanSetting(EncryptedString.of("While Use"), false)
			.setDescription(EncryptedString.of("Will hit the player no matter if you're eating or blocking with a shield"));
	private final BooleanSetting onLeftClick = new BooleanSetting(EncryptedString.of("On Left Click"), false)
			.setDescription(EncryptedString.of("Only gets triggered if holding down left click"));
	private final BooleanSetting allItems = new BooleanSetting(EncryptedString.of("All Items"), false)
			.setDescription(EncryptedString.of("Works with all Items /THIS USES SWORD DELAY AS THE DELAY/"));
	private final MinMaxSetting swordDelay = new MinMaxSetting(EncryptedString.of("Sword Delay"), 0, 1000, 1, 540, 550)
			.setDescription(EncryptedString.of("Delay for swords"));
	private final MinMaxSetting axeDelay = new MinMaxSetting(EncryptedString.of("Axe Delay"), 0, 1000, 1, 780, 800)
			.setDescription(EncryptedString.of("Delay for axes"));
	private final BooleanSetting checkShield = new BooleanSetting(EncryptedString.of("Check Shield"), false)
			.setDescription(EncryptedString.of("Checks if the player is blocking your hits with a shield (Recommended with Shield Disabler)"));
	private final BooleanSetting onlyCritSword = new BooleanSetting(EncryptedString.of("Only Crit Sword"), false)
			.setDescription(EncryptedString.of("Only does critical hits with a sword"));
	private final BooleanSetting onlyCritAxe = new BooleanSetting(EncryptedString.of("Only Crit Axe"), false)
			.setDescription(EncryptedString.of("Only does critical hits with an axe"));
	private final BooleanSetting swing = new BooleanSetting(EncryptedString.of("Swing Hand"), true)
			.setDescription(EncryptedString.of("Whether to swing the hand or not"));
	private final BooleanSetting whileAscend = new BooleanSetting(EncryptedString.of("While Ascending"), false)
			.setDescription(EncryptedString.of("Wont hit if you're ascending from a jump, only if on ground or falling"));
	private final BooleanSetting clickSimulation = new BooleanSetting(EncryptedString.of("Click Simulation"), false)
			.setDescription(EncryptedString.of("Makes the CPS hud think you're legit"));
	private final BooleanSetting strayBypass = new BooleanSetting(EncryptedString.of("Stray Bypass"), false)
			.setDescription(EncryptedString.of("Bypasses stray's Anti-TriggerBot"));
	private final BooleanSetting allEntities = new BooleanSetting(EncryptedString.of("All Entities"), false)
			.setDescription(EncryptedString.of("Will attack all entities"));
	private final BooleanSetting useShield = new BooleanSetting(EncryptedString.of("Use Shield"), false)
			.setDescription(EncryptedString.of("Uses shield if it's in your offhand"));
	private final NumberSetting shieldTime = new NumberSetting(EncryptedString.of("Shield Time"), 100, 1000, 350, 1);
	private final BooleanSetting sticky = new BooleanSetting(EncryptedString.of("Same Player"), false)
			.setDescription(EncryptedString.of("Hits the player that was recently attacked, good for FFA"));

	private final TimerUtils timer = new TimerUtils();
	private final TimerUtils reactionTimer = new TimerUtils();

	private Entity lastHoveredEntity = null;
	private int currentReactionDelay = 0;
	private int currentSwordDelay, currentAxeDelay;

	public TriggerBot() {
		super(EncryptedString.of("Trigger Bot"),
				EncryptedString.of("Automatically hits players for you"),
				-1,
				Category.COMBAT);
		addSettings(inScreen, whileUse, onLeftClick, allItems, swordDelay, axeDelay, checkShield, whileAscend, sticky, onlyCritSword, onlyCritAxe, swing, clickSimulation, strayBypass, allEntities, useShield, shieldTime);
	}

	@Override
	public void onEnable() {
		currentSwordDelay = swordDelay.getRandomValueInt();
		currentAxeDelay = axeDelay.getRandomValueInt();
		lastHoveredEntity = null;
		reactionTimer.reset();
		currentReactionDelay = ThreadLocalRandom.current().nextInt(65, 125);

		eventManager.add(TickListener.class, this);
		eventManager.add(AttackListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		lastHoveredEntity = null;
		eventManager.remove(TickListener.class, this);
		eventManager.remove(AttackListener.class, this);
		super.onDisable();
	}

	private boolean canCrit() {
		return !mc.player.isOnGround()
				&& mc.player.getVelocity().y < -0.01
				&& !mc.player.isClimbing()
				&& !mc.player.isSubmergedInWater()
				&& !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
				&& !mc.player.hasVehicle();
	}

	@Override
	public void onTick() {
		try {
			if (mc.player == null || mc.world == null) {
				lastHoveredEntity = null;
				return;
			}

			if (!inScreen.getValue() && mc.currentScreen != null) {
				lastHoveredEntity = null;
				return;
			}

			if (Argon.INSTANCE.getModuleManager().getModule(Friends.class).antiAttack.getValue() && Argon.INSTANCE.getFriendManager().isAimingOverFriend()) {
				lastHoveredEntity = null;
				return;
			}

			if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
				lastHoveredEntity = null;
				return;
			}

			if (mc.player.isUsingItem() && !whileUse.getValue())
				return;

			if (!whileAscend.getValue() && !mc.player.isOnGround() && mc.player.getVelocity().y > 0)
				return;

			ItemStack mainHand = mc.player.getMainHandStack();
			boolean isSword = mainHand.isIn(ItemTags.SWORDS);
			boolean isAxe = mainHand.isIn(ItemTags.AXES);

			if (!allItems.getValue() && !isSword && !isAxe) {
				lastHoveredEntity = null;
				return;
			}

			if (!(mc.crosshairTarget instanceof EntityHitResult hit)) {
				lastHoveredEntity = null;
				return;
			}

			Entity entity = hit.getEntity();
			if (entity == null) {
				lastHoveredEntity = null;
				return;
			}

			if (entity != lastHoveredEntity) {
				lastHoveredEntity = entity;
				reactionTimer.reset();
				currentReactionDelay = ThreadLocalRandom.current().nextInt(65, 125);
				return;
			}

			if (!reactionTimer.delay(currentReactionDelay))
				return;

			boolean isValidTarget = (entity instanceof PlayerEntity)
					|| (strayBypass.getValue() && entity instanceof ZombieEntity)
					|| (allEntities.getValue() && entity instanceof LivingEntity);

			if (!isValidTarget)
				return;

			if (sticky.getValue() && mc.player.getAttacking() != null && entity != mc.player.getAttacking())
				return;

			if (entity instanceof PlayerEntity player && checkShield.getValue() && player.isBlocking() && !WorldUtils.isShieldFacingAway(player))
				return;

			if (isSword && onlyCritSword.getValue() && !canCrit())
				return;

			if (isAxe && onlyCritAxe.getValue() && !canCrit())
				return;

			if (!isSword && !isAxe && onlyCritSword.getValue() && !canCrit())
				return;

			int delay = isAxe ? currentAxeDelay : currentSwordDelay;

			if (timer.delay(delay)) {
				ItemStack offHand = mc.player.getOffHandStack();
				if (useShield.getValue() && offHand.isOf(Items.SHIELD) && mc.player.isBlocking())
					MouseSimulation.mouseRelease(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

				WorldUtils.hitEntity(entity, swing.getValue());

				if (clickSimulation.getValue()) {
					int clickDuration = ThreadLocalRandom.current().nextInt(35, 70);
					MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, clickDuration);
				}

				int jitter = ThreadLocalRandom.current().nextInt(-15, 20);
				if (isAxe) {
					currentAxeDelay = Math.max(50, axeDelay.getRandomValueInt() + jitter);
				} else {
					currentSwordDelay = Math.max(50, swordDelay.getRandomValueInt() + jitter);
				}
				timer.reset();
			} else {
				ItemStack offHand = mc.player.getOffHandStack();
				if (useShield.getValue() && offHand.isOf(Items.SHIELD) && !mc.player.isBlocking()) {
					int shieldJitter = ThreadLocalRandom.current().nextInt(-25, 25);
					int useFor = Math.max(100, shieldTime.getValueInt() + shieldJitter);
					MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT, useFor);
				}
			}
		} catch (Exception ignored) {}
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
			event.cancel();
	}
}
