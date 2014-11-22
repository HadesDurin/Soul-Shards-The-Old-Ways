package moze_intel.ssr.gameObjs;

import java.util.List;

import moze_intel.ssr.utils.EntityMapper;
import moze_intel.ssr.utils.SSRConfig;
import moze_intel.ssr.utils.SSRLogger;
import moze_intel.ssr.utils.TierHandler;
import moze_intel.ssr.utils.Utils;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class SoulCageTile extends TileEntity implements IInventory {
	private ItemStack inventory;
	private int counter;
	private int updateCounter;
	private int tier;
	private String entName;
	private boolean redstoneActive;
	private boolean initChecks;
	private boolean active;

	public SoulCageTile() {
		counter = 0;
		updateCounter = 0;
		redstoneActive = false;
		initChecks = false;
		active=false;
	}

	@Override
	public void updateEntity() {
		if (worldObj.isRemote) {
			return;
		}

		if (!initChecks) {
			checkRedstone();
			initChecks = true;
		}

		if (this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord) == 0
				|| tier <= 0) {
			updateCounter = 0;
			counter = 0;
			return;
		}

		if (updateCounter == 19) {
			EntityLiving ent = EntityMapper.getNewEntityInstance(this.worldObj,
					entName);

			if (canEntitySpawn(ent)) {
				setMetadata(2);
				active=true;
			} else {
				setMetadata(1);
				active=false;
			}
			updateCounter = 0;
		} else {
			updateCounter += 1;
		}

		if (this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord) <= 1) {
			counter = 0;
			return;
		}

		if (counter >= TierHandler.getCooldown(tier - 1) * 20 - 1) {
			if (SSRConfig.ENABLE_DEBUG) {
				SSRLogger.logDebug("SPAWNING!");
			}
			EntityLiving[] toSpawn = new EntityLiving[TierHandler
					.getNumSpawns(tier - 1)];

			ItemStack heldItem = Utils.getEntityHeldItem(inventory);
			for (int i = 0; i < toSpawn.length; i++) {
				toSpawn[i] = EntityMapper.getNewEntityInstance(this.worldObj,
						entName);

				if ((toSpawn[i] instanceof EntitySlime)) {
					toSpawn[i].getDataWatcher().updateObject(16,
							Byte.valueOf((byte) 1));
				}

				if (heldItem != null) {
					toSpawn[i].setCurrentItemOrArmor(0, heldItem);
				}

				toSpawn[i].getEntityData().setBoolean("SSR", true);
				toSpawn[i].forceSpawn = true;
				toSpawn[i].func_110163_bv();
			}
			spawnEntities(toSpawn);
			counter = 0;
		} else {
			counter += 1;
		}
	}

	public void checkRedstone() {
		if (worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord,
				this.zCoord)) {
			redstoneActive = true;
		} else {
			redstoneActive = false;
		}
	}

	private void setMetadata(int meta) {
		if (this.worldObj.getBlockMetadata(this.xCoord, this.yCoord,
				this.zCoord) != meta) {
			this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord,
					this.zCoord, meta, 2);
		}
	}

	private boolean canEntitySpawn(EntityLiving ent) {
		if ((SSRConfig.ENABLE_FLOOD_PREVENTION) && (hasReachedSpawnLimit(ent))) {
			return false;
		}

		if ((TierHandler.getChecksRedstone(tier - 1))
				&& (redstoneActive == SSRConfig.INVERT_REDSTONE)) {
			return false;
		}

		if ((TierHandler.getChecksPlayer(tier - 1))
				&& (!isPlayerClose(this.xCoord, this.yCoord, this.zCoord))) {
			return false;
		}

		if ((TierHandler.getChecksLight(tier - 1)) && (!canSpawnInLight(ent))) {
			return false;
		}

		if ((TierHandler.getChecksWorld(tier - 1)) && (!canSpawnInWorld(ent))) {
			return false;
		}
		return true;
	}

	private boolean isPlayerClose(int x, int y, int z) {
		return worldObj.getClosestPlayer(x, y, z, 16.0D) != null;
	}

	private boolean canSpawnInWorld(EntityLiving ent) {
		int dimension = worldObj.provider.dimensionId;

		if ((ent instanceof EntitySkeleton)) {
			EntitySkeleton skele = (EntitySkeleton) ent;

			if ((skele.getSkeletonType() == 1) && (dimension == -1)) {
				return true;
			}
			if (dimension == 0) {
				return true;
			}
			return false;
		}

		if (((ent instanceof EntityBlaze))
				|| ((ent instanceof EntityPigZombie))
				|| ((ent instanceof EntityGhast))
				|| ((ent instanceof EntityMagmaCube))) {
			return dimension == -1;
		}
		if ((ent instanceof EntityEnderman)) {
			return dimension == 1;
		}
		return true;
	}

	private boolean canSpawnInLight(EntityLiving ent) {
		int light = worldObj.getBlockLightValue(xCoord, yCoord, zCoord);
		if (((ent instanceof EntityMob)) || ((ent instanceof IMob))) {
			return light <= 8;
		}
		if (((ent instanceof EntityAnimal)) || ((ent instanceof IAnimals))) {
			return light > 8;
		}
		return true;
	}

	private boolean canSpawnAtCoords(EntityLiving ent) {
		return worldObj.getCollidingBoundingBoxes(ent, ent.boundingBox)
				.isEmpty();
	}

	private boolean hasReachedSpawnLimit(EntityLiving ent) {
		AxisAlignedBB aabb = AxisAlignedBB
				.getBoundingBox(xCoord - 16, yCoord - 16, zCoord - 16,
						xCoord + 16, yCoord + 16, zCoord + 16);
		int mobCount = 0;

		for (EntityLiving entity : (List<EntityLiving>) worldObj
				.getEntitiesWithinAABB(ent.getClass(), aabb)) {
			if (entity.getEntityData().getBoolean("SSR")) {
				mobCount++;
			}
		}
		return mobCount >= SSRConfig.MAX_NUM_ENTITIES;
	}

	private void spawnEntities(EntityLiving[] ents) {
		for (EntityLiving ent : ents) {
			int counter = 0;
			do {
				counter++;
				if (counter >= 5) {
					ent.setDead();
					break;
				}
				double x = xCoord
						+ (worldObj.rand.nextDouble() - worldObj.rand
								.nextDouble()) * 4.0D;
				double y = yCoord + worldObj.rand.nextInt(3) - 1;
				double z = zCoord
						+ (worldObj.rand.nextDouble() - worldObj.rand
								.nextDouble()) * 4.0D;
				ent.setLocationAndAngles(x, y, z,
						worldObj.rand.nextFloat() * 360.0F, 0.0F);
			} while (!canSpawnAtCoords(ent) || counter >= 5);
			if (!ent.isDead) {
				worldObj.spawnEntityInWorld(ent);
			}
		}
	}

	public void invalidate() {
		super.invalidate();
		initChecks = false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		inventory = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("Shard"));

		if (inventory != null) {
			tier = Utils.getShardTier(inventory);
			entName = Utils.getShardBoundEnt(inventory);
		}
		active=nbt.getBoolean("active");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		if (inventory != null) {
			NBTTagCompound tag = new NBTTagCompound();
			inventory.writeToNBT(tag);
			nbt.setTag("Shard", tag);
		}
		nbt.setBoolean("active", this.active);
	}

	public int getSizeInventory() {
		return 0;
	}

	public ItemStack getStackInSlot(int slot) {
		return inventory;
	}

	public ItemStack decrStackSize(int slot, int qty) {
		if (qty == 0) {
			return null;
		}
		ItemStack stack = inventory.copy();
		inventory = null;

		this.worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 2);
		this.tier = 0;
		this.entName = null;

		return stack;
	}

	public void setInventorySlotContents(int slot, ItemStack stack) {
		this.inventory = stack;

		this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord,
				this.zCoord, 1, 2);
		this.tier = Utils.getShardTier(this.inventory);
		this.entName = Utils.getShardBoundEnt(this.inventory);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		return null;
	}

	@Override
	public String getInventoryName() {
		return null;
	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	@Override
	public int getInventoryStackLimit() {
		return 1;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return false;
	}

	@Override
	public void openInventory() {
	}

	@Override
	public void closeInventory() {
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return stack != null && stack.getItem() == ObjHandler.SOUL_SHARD
				&& Utils.isShardBound(stack) && Utils.getShardTier(stack) > 0;
	}
	
	public String getEntityName(){
		return this.entName;
	}
	
	public int getTier(){
		return this.tier;
	}
	
	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		this.readFromNBT(pkt.func_148857_g());
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound();
		this.writeToNBT(tag);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
	}
}