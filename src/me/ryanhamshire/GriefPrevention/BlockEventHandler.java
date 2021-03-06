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
import org.bukkit.ChatColor;
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
        //cache last claim for the FromBlockToEvent to opimize lava flow processing
        private Claim lastBlockFromToEventClaim;
        //cache for the BlockBurnEvent
        private Claim lastBlockBurnEventClaim;
	
	//boring typical constructor
	public BlockEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
	}
	
	//when a player breaks a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
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
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
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
				GriefPrevention.sendMessage(player, ChatColor.DARK_AQUA, "This chest is not protected. Consider expanding an existing claim or creating a new one.");				
			}
                }
                // else if (block.getType() == Material.TNT && !playerData.ignoreClaims) {
                //         // Deny TNT placement outside claim with trust
                //         GriefPrevention.sendMessage(player, TextMode.Err, "You cannot place TNT outside of your own claims.");
                //         placeEvent.setCancelled(true);
                //         return;
                // }
		
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
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockIgnite (BlockIgniteEvent event)
	{
                // Deny usage of flint and steel or fireballs without build permission
                if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL || event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
                        Player player = event.getPlayer();
                        if (player != null) {
                                PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
                                Claim claim = dataStore.getClaimAt(event.getBlock().getLocation(), true, playerData.lastClaim);
                                // Deny ignition if it player has no build permissions unless it is for a nether portal
                                if (GriefPrevention.instance.config_claims_firePlacementRequiresTrust && claim == null && !playerData.ignoreClaims && !player.hasPermission("griefprevention.dangerousitems")) {
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
                // Deny lava ignition across claims
                if (GriefPrevention.instance.config_claims_fireCannotCrossClaimBorders && event.getCause() == IgniteCause.LAVA) {
                        boolean hasSource = false;
                        Claim destClaim = dataStore.getClaimAt(event.getBlock().getLocation(), true, null);
                mainLoop: for (int x = -1; x <= 1; ++x) {
                                for (int y = -5; y <= 1; ++y) {
                                        for (int z = -1; z <= 1; ++z) {
                                                Block srcBlock = event.getBlock().getRelative(x, y, z);
                                                if (srcBlock.getType() != Material.LAVA && srcBlock.getType() != Material.STATIONARY_LAVA) continue;
                                                if (destClaim == null && dataStore.getClaimAt(srcBlock.getLocation(), true, null) != null) continue;
                                                if (destClaim != null && !destClaim.contains(srcBlock.getLocation(), true, false)) continue;
                                                hasSource = true;
                                                break mainLoop;
                                        }
                                }
                        }
                        if (!hasSource) event.setCancelled(true);
                }
        }

        /**
         * When a block burns up it will sometimes turn into a new
         * fire block. This event does not tell us where the fire
         * block is that causes this block to burn. So, we
         * manually check if there is a fire block within a 3x3x3
         * cuboid around the burned block within the same claim or
         * if both are not in a claim. If not, the spread must be
         * cross claim and will be cancelled.
         */
        @EventHandler(priority = EventPriority.HIGH)
        public void onBlockBurn(BlockBurnEvent event) {
                if (!GriefPrevention.instance.config_claims_fireCannotCrossClaimBorders) return;
                Claim claim = dataStore.getClaimAt(event.getBlock().getLocation(), true, lastBlockBurnEventClaim);
                if (claim != null) lastBlockBurnEventClaim = claim;
                for (int x = -1; x <= 1; ++x) {
                        for (int y = -1; y <= 1; ++y) {
                                for (int z = -1; z <= 1; ++z) {
                                        Block source = event.getBlock().getRelative(x, y, z);
                                        if (source.getType() != Material.FIRE) continue;
                                        if (claim == null) {
                                                if (dataStore.getClaimAt(source.getLocation(), true, lastBlockBurnEventClaim) == null) return;
                                        } else {
                                                if (claim.contains(source.getLocation(), true, false)) return;
                                        }
                                }
                        }
                }
                event.setCancelled(true);
        }
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockSpread(BlockSpreadEvent event)
	{
                if (!GriefPrevention.instance.config_claims_fireCannotCrossClaimBorders) return;
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
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockFromTo (BlockFromToEvent spreadEvent)
	{
                if (!GriefPrevention.instance.config_claims_fireCannotCrossClaimBorders) return;

		Block fromBlock = spreadEvent.getBlock();
                // Ignore water; water griefing is unlikely and we don't like exploits
                if (fromBlock.getType() != Material.STATIONARY_LAVA && fromBlock.getType() != Material.LAVA) return;
		Claim fromClaim = this.dataStore.getClaimAt(fromBlock.getLocation(), false, lastBlockFromToEventClaim);
                if (fromClaim != null) lastBlockFromToEventClaim = fromClaim;

		//where to?
		Block toBlock = spreadEvent.getToBlock();		
		Claim toClaim = this.dataStore.getClaimAt(toBlock.getLocation(), false, lastBlockFromToEventClaim);
                if (toClaim != null) lastBlockFromToEventClaim = toClaim;
		
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
