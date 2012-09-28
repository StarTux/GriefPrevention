/*
  GriefPrevention Server Plugin for Minecraft
  Copyright (C) 2012 Ryan Hamshire

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package me.ryanhamshire.GriefPrevention;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

//event handlers related to blocks
public class BlockEventHandler implements Listener 
{
	//convenience reference to singleton datastore
	private DataStore dataStore;
	
	//boring typical constructor
	public BlockEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
	}
	
	//when a player breaks a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockBreak(BlockBreakEvent breakEvent)
	{
		Player player = breakEvent.getPlayer();
		Block block = breakEvent.getBlock();		
		
		//make sure the player is allowed to break at the location
		String noBuildReason = GriefPrevention.instance.allowBreak(player, block.getLocation());
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			breakEvent.setCancelled(true);
			return;
		}
		
		PlayerData playerData = this.dataStore.getPlayerData(player.getName());
		Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
		
		//if there's a claim here
		if(claim != null)
		{
			//if breaking UNDER the claim
			if(block.getY() < claim.lesserBoundaryCorner.getBlockY())
			{
				//extend the claim downward beyond the breakage point
				this.dataStore.extendClaim(claim, claim.getLesserBoundaryCorner().getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
			}
		}
	}
	
	//when a player places a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		Block block = placeEvent.getBlock();
		
		//make sure the player is allowed to build at the location
		String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			placeEvent.setCancelled(true);
			return;
		}
		
		//if the block is being placed within an existing claim
		PlayerData playerData = this.dataStore.getPlayerData(player.getName());
		Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
		if(claim != null)
		{
			//if the player has permission for the claim and he's placing UNDER the claim
			if(block.getY() < claim.lesserBoundaryCorner.getBlockY())
			{
				//extend the claim downward
				this.dataStore.extendClaim(claim, claim.getLesserBoundaryCorner().getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
			}
                        //FEATURE: automatically create a claim when a player who has no claims places a chest
		
                        //otherwise if there's no claim, the player is placing a chest, and new player automatic claims are enabled
		} else if(block.getType() == Material.CHEST && GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1 && GriefPrevention.instance.claimsEnabledForWorld(block.getWorld()))
		{			
			//if the chest is too deep underground, don't create the claim and explain why
			if(GriefPrevention.instance.config_claims_preventTheft && block.getY() < GriefPrevention.instance.config_claims_maxDepth)
			{
				GriefPrevention.sendMessage(player, TextMode.Warn, "This chest can't be protected because it's too deep underground.  Consider moving it.");
				return;
			}
			
			int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;
			
			//if the player doesn't have any claims yet, automatically create a claim centered at the chest
			if(playerData.claims.size() == 0)
			{
				//radius == 0 means protect ONLY the chest
				if(GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius == 0)
				{					
					this.dataStore.createClaim(block.getWorld(), block.getX(), block.getX(), block.getY(), block.getY(), block.getZ(), block.getZ(), player.getName(), null);
					GriefPrevention.sendMessage(player, TextMode.Success, "This chest is protected.");						
				}
				
				//otherwise, create a claim in the area around the chest
				else
				{
					//as long as the automatic claim overlaps another existing claim, shrink it
					//note that since the player had permission to place the chest, at the very least, the automatic claim will include the chest
					while(radius >= 0 && !this.dataStore.createClaim(block.getWorld(), 
                                                                                         block.getX() - radius, block.getX() + radius, 
                                                                                         block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, block.getY(), 
                                                                                         block.getZ() - radius, block.getZ() + radius, 
                                                                                         player.getName(), 
                                                                                         null).succeeded)
					{
						radius--;
					}
					
					//notify and explain to player
					GriefPrevention.sendMessage(player, TextMode.Success, "This chest and nearby blocks are protected from breakage and theft.  The gold and glowstone blocks mark the protected area.");
					
					//show the player the protected area
					Claim newClaim = this.dataStore.getClaimAt(block.getLocation(), false, null);
					Visualization visualization = Visualization.FromClaim(newClaim, block.getY(), VisualizationType.Claim);
					Visualization.Apply(player, visualization);
				}
				
				//instructions for using /trust
				GriefPrevention.sendMessage(player, TextMode.Instr, "Use the /trust command to grant other players access.");
				
				//unless special permission is required to create a claim with the shovel, educate the player about the shovel
				if(!GriefPrevention.instance.config_claims_creationRequiresPermission)
				{
					GriefPrevention.sendMessage(player, TextMode.Instr, "To claim more land, use a golden shovel.");
				}
			}
			
			//check to see if this chest is in a claim, and warn when it isn't
			if(GriefPrevention.instance.config_claims_preventTheft && this.dataStore.getClaimAt(block.getLocation(), false, playerData.lastClaim) == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Warn, "This chest is NOT protected.  Consider expanding an existing claim or creating a new one.");				
			}
                }
                // else if (block.getType() == Material.TNT && !playerData.ignoreClaims) {
                //         // Deny TNT placement outside claim with trust
                //         GriefPrevention.sendMessage(player, TextMode.Err, "You cannot place TNT outside of your own claims.");
                //         placeEvent.setCancelled(true);
                //         return;
                // }
		
	}
	
	//blocks "pushing" other players' blocks around (pistons)
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockPistonExtend (BlockPistonExtendEvent event)
	{		
		//who owns the piston, if anyone?
		String pistonClaimOwnerName = "_";
		Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
		if(claim != null) pistonClaimOwnerName = claim.getOwnerName();
		
		//which blocks are being pushed?
		List<Block> blocks = event.getBlocks();
		for(int i = 0; i < blocks.size(); i++)
		{
			//if ANY of the pushed blocks are owned by someone other than the piston owner, cancel the event
			Block block = blocks.get(i);
			claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
			if(claim != null && !claim.getOwnerName().equals(pistonClaimOwnerName))
			{
				event.setCancelled(true);
				return;
			}
		}
	}
	
	//blocks theft by pulling blocks out of a claim (again pistons)
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockPistonRetract (BlockPistonRetractEvent event)
	{
		//we only care about sticky pistons
		if(!event.isSticky()) return;
				
		//who owns the moving block, if anyone?
		String movingBlockOwnerName = "_";
		Claim movingBlockClaim = this.dataStore.getClaimAt(event.getRetractLocation(), false, null);
		if(movingBlockClaim != null) movingBlockOwnerName = movingBlockClaim.getOwnerName();
		
		//who owns the piston, if anyone?
		String pistonOwnerName = "_";
		Location pistonLocation = event.getBlock().getLocation();		
		Claim pistonClaim = this.dataStore.getClaimAt(pistonLocation, false, null);
		if(pistonClaim != null) pistonOwnerName = pistonClaim.getOwnerName();
		
		//if there are owners for the blocks, they must be the same player
		//otherwise cancel the event
		if(!pistonOwnerName.equals(movingBlockOwnerName))
		{
			event.setCancelled(true);
		}		
	}

        /**
         * Check whether a given location could be the ignition
         * point of a nether portal.  This is needed to
         * exceptionally allow ignition of blocks outside your own
         * claims if the purpose is to create a nether portal.
         * This works only with the bottom two blocks.
         * @param location The location
         * @return true if a nether portal was found, false otherwise.
         */
        public boolean checkNetherPortal(Location location) {
                final int[][][] locations = {{{1, -1, 0}, {2, 0, 0}, {2, 1, 0}, {2, 2, 0}, {-1, 0, 0}, {-1, 1, 0}, {-1, 2, 0}, {1, 3, 0}},
                                       {{-1, -1, 0}, {-2, 0, 0}, {-2, 1, 0}, {-2, 2, 0}, {1, 0, 0}, {1, 1, 0}, {1, 2, 0}, {-1, 3, 0}},
                                       {{0, -1, 1}, {0, 0, 2}, {0, 1, 2}, {0, 2, 2}, {0, 0, -1}, {0, 1, -1}, {0, 2, -1}, {0, 3, 1}},
                                       {{0, -1, -1}, {0, 0, -2}, {0, 1, -2}, {0, 2, -2}, {0, 0, 1}, {0, 1, 1}, {0, 2, 1}, {0, 3, -1}}};
                final int obs = Material.OBSIDIAN.getId();
                int x = location.getBlockX();
                int y = location.getBlockY();
                int z = location.getBlockZ();
                World world = location.getWorld();
                if (world.getBlockTypeIdAt(x, y - 1, z) != obs) {
                        return false;
                }
                if (world.getBlockTypeIdAt(x, y + 3, z) != obs) {
                        return false;
                }
        mainLoop: for (int nPortal = 0; nPortal < 4; ++nPortal) {
                        for (int nCoord = 0; nCoord < 8; ++nCoord) {
                                int l[] = locations[nPortal][nCoord];
                                if (world.getBlockTypeIdAt(x + l[0], y + l[1], z + l[2]) != obs) {
                                        continue mainLoop;
                                }
                        }
                        return true;
                }
                return false;
        }
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockIgnite (BlockIgniteEvent event)
	{
                // Deny usage of flint and steel or fireballs without build permission
                if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL || event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
                        Player player = event.getPlayer();
                        if (player != null) {
                                PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
                                Claim claim = dataStore.getClaimAt(event.getBlock().getLocation(), true, playerData.lastClaim);
                                // Deny ignition if it player has no build permissions unless it is for a nether portal
                                if (claim == null && !playerData.ignoreClaims) {
                                        if (!checkNetherPortal(event.getBlock().getLocation())) {
                                                GriefPrevention.sendMessage(player, TextMode.Err, "You cannot do that outside your own claims.");
                                                event.setCancelled(true);
                                                return;
                                        }
                                } else if (claim != null) {
                                        // if the block is above or below claim borders and is for a nether portal, we allow it. Stupid exception.
                                        if (dataStore.getClaimAt(event.getBlock().getLocation(), false, claim) == null && checkNetherPortal(event.getBlock().getLocation())) return;
                                        String noBuildReason = claim.allowBuild(player);
                                        if (noBuildReason != null) {
                                                GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                                                event.setCancelled(true);
                                                return;
                                        }
                                }
                        }
                }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onBlockBurn(BlockBurnEvent event) {
                if (event.getBlock().getType() == Material.TNT) return;
                event.getBlock().setType(Material.AIR);
                event.setCancelled(true);
        }
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockSpread(BlockSpreadEvent event)
	{
                if (event.getNewState().getType() != Material.FIRE) return;
                // Deny fire spread into or out of claims.
                Block srcBlock = event.getSource();
                Block dstBlock = event.getBlock();
                Claim dstClaim = dataStore.getClaimAt(dstBlock.getLocation(), true, null);
                Claim srcClaim = dataStore.getClaimAt(srcBlock.getLocation(), true, dstClaim);
                // We must not check if the sourceblock is fire
                // since the event sometimes reports air when fire
                // spreads.
                if (dstClaim != srcClaim) {
                        // If both blocks are within different claims of the same owner, do nothing.
                        if (dstClaim != null && srcClaim != null && dstClaim.getOwnerName().equalsIgnoreCase(srcClaim.getOwnerName())) {
                        } else {
                                event.setCancelled(true);
                                return;
                        }
                }
        }
	
	//ensures fluids don't flow out of claims, unless into another claim where the owner is trusted to build
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockFromTo (BlockFromToEvent spreadEvent)
	{
		//from where?
		Block fromBlock = spreadEvent.getBlock();
		Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, null);

                // Ignore water; water griefing is unlikely and we don't like exploits
                if (fromBlock.getType() == Material.STATIONARY_WATER || fromBlock.getType() == Material.WATER) return;
		
		//where to?
		Block toBlock = spreadEvent.getToBlock();		
		Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, fromClaim);
		
		//block any spread into the wilderness
		if(fromClaim != null && toClaim == null)
		{
			spreadEvent.setCancelled(true);
			return;
		}
		
		//if spreading into a claim
		else if(toClaim != null)
		{		
			//who owns the spreading block, if anyone?
			OfflinePlayer fromOwner = null;			
			if(fromClaim != null)
			{
				//if it's within the same claim, allow it
				if(fromClaim == toClaim) return;				
				
				fromOwner = GriefPrevention.instance.getServer().getOfflinePlayer(fromClaim.ownerName);
			}
			
			//cancel unless the owner of the spreading block is allowed to build in the receiving claim
			if(fromOwner == null || fromOwner.getPlayer() == null || toClaim.allowBuild(fromOwner.getPlayer()) != null)
			{
				spreadEvent.setCancelled(true);
			}
		}
	}
}
