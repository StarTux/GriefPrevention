/*
  GriefPrevention Server Plugin for Minecraft
  Copyright (C) 2011 Ryan Hamshire

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

class PlayerEventHandler implements Listener 
{
	private DataStore dataStore;
	
	//number of milliseconds in a day
	private final long MILLISECONDS_IN_DAY = 1000 * 60 * 60 * 24;
	
	//typical constructor, yawn
	PlayerEventHandler(DataStore dataStore, GriefPrevention plugin)
	{
		this.dataStore = dataStore;
	}
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
	void onPlayerChat (AsyncPlayerChatEvent event)
	{		
		final Player player = event.getPlayer();
		String message = event.getMessage();
		//FEATURE: automatically educate players about the /trapped command
		//check for "trapped" or "stuck" to educate players about the /trapped command
		if(message.contains("trapped") || message.contains("stuck"))
		{
                        new BukkitRunnable() {
                                public void run() {
                                        GriefPrevention.sendMessage(player, TextMode.Info, "Are you trapped in someone's claim?  Consider the /trapped command.");
                                }
                        }.runTask(GriefPrevention.instance);
		}
	}
	
	//if two strings are 75% identical, they're too close to follow each other in the chat
	private boolean stringsAreSimilar(String message, String lastMessage)
	{
		//determine which is shorter
		String shorterString, longerString;
		if(lastMessage.length() < message.length())
		{
			shorterString = lastMessage;
			longerString = message;
		}
		else
		{
			shorterString = message;
			longerString = lastMessage;
		}
		
		if(shorterString.length() <= 5) return shorterString.equals(longerString);
		
		//set similarity tolerance
		int maxIdenticalCharacters = longerString.length() - longerString.length() / 4;
		
		//trivial check on length
		if(shorterString.length() < maxIdenticalCharacters) return false;
		
		//compare forward
		int identicalCount = 0;
		for(int i = 0; i < shorterString.length(); i++)
		{
			if(shorterString.charAt(i) == longerString.charAt(i)) identicalCount++;
			if(identicalCount > maxIdenticalCharacters) return true;
		}
		
		//compare backward
		for(int i = 0; i < shorterString.length(); i++)
		{
			if(shorterString.charAt(shorterString.length() - i - 1) == longerString.charAt(longerString.length() - i - 1)) identicalCount++;
			if(identicalCount > maxIdenticalCharacters) return true;
		}
		
		return false;
	}

	//when a player successfully joins the server...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	void onPlayerJoin(PlayerJoinEvent event)
	{
		String playerName = event.getPlayer().getName();
		
		//note login time
		PlayerData playerData = this.dataStore.getPlayerData(playerName);
		playerData.lastLogin = new Date();
		this.dataStore.savePlayerData(playerName, playerData);
	}
	
	//when a player quits...
	@EventHandler(priority = EventPriority.HIGHEST)
	void onPlayerQuit(PlayerQuitEvent event)
	{
		String playerName = event.getPlayer().getName();
		PlayerData playerData = this.dataStore.getPlayerData(playerName);
		
		//remember logout time
		playerData.lastLogout = Calendar.getInstance().getTimeInMillis();
	}
	
	//when a player drops an item
	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerDropItem(PlayerDropItemEvent event)
	{
		Player player = event.getPlayer();
		//in creative worlds, dropping items is blocked
		if(GriefPrevention.instance.creativeRulesApply(player.getLocation()))
		{
			event.setCancelled(true);
			return;
		}
	}
	
	//when a player interacts with an entity...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
	{
		Player player = event.getPlayer();
		Entity entity = event.getRightClicked();
		
		//if the entity is a vehicle and we're preventing theft in claims		
		if(GriefPrevention.instance.config_claims_preventTheft && entity instanceof Vehicle)
		{
			//if the entity is in a claim
                        PlayerData data = this.dataStore.getPlayerData(player.getName());
			Claim claim = this.dataStore.getClaimAt(entity.getLocation(), false, data.lastClaim);
			if(claim != null)
			{
				//for storage and powered minecarts, apply container rules (this is a potential theft)
				if(entity instanceof StorageMinecart || entity instanceof PoweredMinecart)
				{					
					String noContainersReason = claim.allowContainers(player);
					if(noContainersReason != null)
					{
						GriefPrevention.sendMessage(player, TextMode.Err, noContainersReason);
						event.setCancelled(true);
					}
				}
				
				//for boats, apply access rules
				else if(entity instanceof Boat)
				{
					String noAccessReason = claim.allowAccess(player);
					if(noAccessReason != null)
					{
						GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
						event.setCancelled(true);
					}
				}
				
				//if the entity is an animal, apply container rules
				else if(entity instanceof Animals)
				{
					if(claim.allowContainers(player) != null)
					{
						GriefPrevention.sendMessage(player, TextMode.Err, "That animal belongs to " + claim.getOwnerName() + ".");
						event.setCancelled(true);
					}
				}
			}
		} else if (entity instanceof ItemFrame) {
                        ItemFrame itemFrame = (ItemFrame)entity;
                        if (itemFrame.getItem().getType() != Material.AIR) {
                                PlayerData playerData = dataStore.getPlayerData(player.getName());
                                Claim claim = dataStore.getClaimAt(itemFrame.getLocation(), false, playerData.lastClaim);
                                if (claim != null) {
                                        String noContainerReason = claim.allowBuild(player);
                                        if (noContainerReason != null) {
                                                GriefPrevention.sendMessage(player, TextMode.Err, noContainerReason);
                                                event.setCancelled(true);
                                        }
                                }
                        }
                } else if (entity instanceof Villager) { // if the entity is a villager, apply container rules
                        PlayerData data = this.dataStore.getPlayerData(player.getName());
			Claim claim = this.dataStore.getClaimAt(entity.getLocation(), false, data.lastClaim);
                        if (claim != null && claim.allowContainers(player) != null) {
                                GriefPrevention.sendMessage(player, TextMode.Err, "This villager belongs to " + claim.getOwnerName() + ".");
                                event.setCancelled(true);
                        }
                }
	}
	
	//when a player switches in-hand items
	@EventHandler(ignoreCancelled = true)
	public void onItemHeldChange(PlayerItemHeldEvent event)
	{
		Player player = event.getPlayer();
		
		//if he's switching to the golden shovel
		ItemStack newItemStack = player.getInventory().getItem(event.getNewSlot());
		if(newItemStack != null && newItemStack.getType() == Material.GOLD_SPADE)
		{
			EquipShovelProcessingTask task = new EquipShovelProcessingTask(player);
			GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 15L);  //15L is approx. 3/4 of a second
		}
	}
	
	//block players from entering beds they don't have permission for
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerBedEnter (PlayerBedEnterEvent bedEvent)
	{
		if(!GriefPrevention.instance.config_claims_preventButtonsSwitches) return;
		
		Player player = bedEvent.getPlayer();
		Block block = bedEvent.getBed();
		
		//if the bed is in a claim 
		Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
		if(claim != null)
		{
			//if the player doesn't have access in that claim, tell him so and prevent him from sleeping in the bed
			if(claim.allowAccess(player) != null)
			{
				bedEvent.setCancelled(true);
				GriefPrevention.sendMessage(player, TextMode.Err, claim.getOwnerName() + " hasn't given you permission to sleep here.");
			}
		}		
	}
	
	//block use of buckets within other players' claims
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerBucketEmpty (PlayerBucketEmptyEvent bucketEvent)
	{
		Player player = bucketEvent.getPlayer();
		Block block = bucketEvent.getBlockClicked().getRelative(bucketEvent.getBlockFace());
		
		//make sure the player is allowed to build at the location
		String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			bucketEvent.setCancelled(true);
                        return;
		}

                if (GriefPrevention.instance.config_claims_firePlacementRequiresTrust) {
                        // deny placement of lava outside a claim with build permissions
                        PlayerData playerData = dataStore.getPlayerData(player.getName());
                        Claim claim = dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
                        if (bucketEvent.getBucket() == Material.LAVA_BUCKET && claim == null && !playerData.ignoreClaims && !player.hasPermission("griefprevention.dangerousitems")) {
                                // The absence of a claim is enough because we already
                                // know that the player has build perms
                                GriefPrevention.sendMessage(player, TextMode.Err, "You cannot place that outside your own claim.");
                                bucketEvent.setCancelled(true);
                                return;
                        }
                }
	}
	
	//see above
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPlayerBucketFill (PlayerBucketFillEvent bucketEvent)
	{
		Player player = bucketEvent.getPlayer();
		Block block = bucketEvent.getBlockClicked();
		
		//make sure the player is allowed to build at the location
		String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation());
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			bucketEvent.setCancelled(true);
			return;
		}
	}

        @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
        void onPlayerInteractTool(PlayerInteractEvent event) {
                Player player = event.getPlayer();
                //what's the player holding?
                Material materialInHand = player.getItemInHand().getType();
                Block clickedBlock = event.getClickedBlock();  //null returned here means interacting with air
                if(clickedBlock == null) {
                        try {
				//try to find a far away non-air block along line of sight
				clickedBlock = player.getTargetBlock(null, 250);
                        } catch(Exception e) {
                                //an exception intermittently comes from getTargetBlock().  when it does, just ignore the event
                                return;
                        }
                }
                Material clickedBlockType = clickedBlock.getType();
                //if it's a stick, he's investigating a claim			
                if(materialInHand == Material.STICK)
                {
                        //air indicates too far away
                        if(clickedBlockType == Material.AIR)
                        {
                                GriefPrevention.sendMessage(player, TextMode.Err, "That's too far away.");
                                return;
                        }
                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false /*ignore height*/, null);
                        //no claim case
                        if(claim == null)
                        {
                                GriefPrevention.sendMessage(player, TextMode.Info, "No one has claimed this block.");
                                Visualization.Revert(player);
                        }
                        //claim case
                        else
                        {
                                GriefPrevention.sendMessage(player, TextMode.Info, "This block has been claimed by " + claim.getOwnerName() + ".");
                                //visualize boundary
                                Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.Claim);
                                Visualization.Apply(player, visualization);
                        }
                        return;
                }
                //if it's a golden shovel
                else if(materialInHand != Material.GOLD_SPADE) return;
                PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                //can't use the shovel from too far away
                if(clickedBlockType == Material.AIR)
                {
                        GriefPrevention.sendMessage(player, TextMode.Err, "That's too far away!");
                        return;
                }
                String playerName = player.getName();
                playerData = this.dataStore.getPlayerData(player.getName());
                //if the player doesn't have claims permission, don't do anything
                if(GriefPrevention.instance.config_claims_creationRequiresPermission && !player.hasPermission("griefprevention.createclaims"))
                {
                        GriefPrevention.sendMessage(player, TextMode.Err, "You don't have permission to claim land.");
                        return;
                }
                //if he's resizing a claim and that claim hasn't been deleted since he started resizing it
                if(playerData.claimResizing != null && playerData.claimResizing.inDataStore)
                {
                        if(clickedBlock.getLocation().equals(playerData.lastShovelLocation)) return;
                        //figure out what the coords of his new claim would be
                        int newx1, newx2, newz1, newz2, newy1, newy2;
                        if(playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getLesserBoundaryCorner().getBlockX())
                        {
                                newx1 = clickedBlock.getX();
                        }
                        else
                        {
                                newx1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockX();
                        }
                        if(playerData.lastShovelLocation.getBlockX() == playerData.claimResizing.getGreaterBoundaryCorner().getBlockX())
                        {
                                newx2 = clickedBlock.getX();
                        }
                        else
                        {
                                newx2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockX();
                        }
                        if(playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getLesserBoundaryCorner().getBlockZ())
                        {
                                newz1 = clickedBlock.getZ();
                        }
                        else
                        {
                                newz1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockZ();
                        }
                        if(playerData.lastShovelLocation.getBlockZ() == playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ())
                        {
                                newz2 = clickedBlock.getZ();
                        }
                        else
                        {
                                newz2 = playerData.claimResizing.getGreaterBoundaryCorner().getBlockZ();
                        }
                        newy1 = playerData.claimResizing.getLesserBoundaryCorner().getBlockY();
                        newy2 = clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance;
                        //for top level claims, apply size rules and claim blocks requirement
                        if(playerData.claimResizing.parent == null)
                        {				
                                //measure new claim, apply size rules
                                int newWidth = (Math.abs(newx1 - newx2) + 1);
                                int newHeight = (Math.abs(newz1 - newz2) + 1);
                                if(!playerData.claimResizing.isAdminClaim() && (newWidth < GriefPrevention.instance.config_claims_minSize || newHeight < GriefPrevention.instance.config_claims_minSize))
                                {
                                        GriefPrevention.sendMessage(player, TextMode.Err, "This new size would be too small.  Claims must be at least " + GriefPrevention.instance.config_claims_minSize + " x " + GriefPrevention.instance.config_claims_minSize + ".");
                                        return;
                                }
                                //make sure player has enough blocks to make up the difference
                                if(!playerData.claimResizing.isAdminClaim())
                                {
                                        int newArea =  newWidth * newHeight;
                                        int blocksRemainingAfter = playerData.getRemainingClaimBlocks() + playerData.claimResizing.getArea() - newArea;
                                        if(blocksRemainingAfter < 0)
                                        {
                                                GriefPrevention.sendMessage(player, TextMode.Err, "You don't have enough blocks for this size.  You need " + Math.abs(blocksRemainingAfter) + " more.");
                                                return;
                                        }
                                }
                        }
                        //special rules for making a top-level claim smaller.  to check this, verifying the old claim's corners are inside the new claim's boundaries.
                        //rule1: in creative mode, top-level claims can't be moved or resized smaller.
                        //rule2: in any mode, shrinking a claim removes any surface fluids
                        Claim oldClaim = playerData.claimResizing;
                        if(oldClaim.parent == null)
                        {				
                                //temporary claim instance, just for checking contains()
                                Claim newClaim = new Claim(
                                        new Location(oldClaim.getLesserBoundaryCorner().getWorld(), newx1, newy1, newz1), 
                                        new Location(oldClaim.getLesserBoundaryCorner().getWorld(), newx2, newy2, newz2),
                                        "", new String[]{}, new String[]{}, new String[]{}, new String[]{});
                                //if the new claim is smaller
                                if(!newClaim.contains(oldClaim.getLesserBoundaryCorner(), true, false) || !newClaim.contains(oldClaim.getGreaterBoundaryCorner(), true, false))
                                {
                                        //enforce creative mode rule
                                        if(!player.hasPermission("griefprevention.deleteclaims") && GriefPrevention.instance.creativeRulesApply(player.getLocation()))
                                        {
                                                GriefPrevention.sendMessage(player, TextMode.Err, "You can't un-claim creative mode land.  You can only make this claim larger or create additional claims.");
                                                return;
                                        }
                                }
                        }
                        //ask the datastore to try and resize the claim, this checks for conflicts with other claims
                        CreateClaimResult result = GriefPrevention.instance.dataStore.resizeClaim(playerData.claimResizing, newx1, newx2, newy1, newy2, newz1, newz2);
                        if(result.succeeded)
                        {
                                //inform and show the player
                                GriefPrevention.sendMessage(player, TextMode.Success, "Claim resized.  You now have " + playerData.getRemainingClaimBlocks() + " available claim blocks.");
                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim);
                                Visualization.Apply(player, visualization);
                                //if resizing someone else's claim, make a log entry
                                if(!playerData.claimResizing.ownerName.equals(playerName))
                                {
                                        GriefPrevention.addLogEntry(playerName + " resized " + playerData.claimResizing.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(playerData.claimResizing.lesserBoundaryCorner) + ".");
                                }
                                //clean up
                                playerData.claimResizing = null;
                                playerData.lastShovelLocation = null;
                        }
                        else
                        {
                                //inform player
                                GriefPrevention.sendMessage(player, TextMode.Err, "Can't resize here because it would overlap another nearby claim.");
                                //show the player the conflicting claim
                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim);
                                Visualization.Apply(player, visualization);
                        }
                        return;
                }
                //otherwise, since not currently resizing a claim, must be starting a resize, creating a new claim, or creating a subdivision
                Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), true /*ignore height*/, playerData.lastClaim);			
                //if within an existing claim, he's not creating a new one
                if(claim != null)
                {
                        //if the player has permission to edit the claim or subdivision
                        String noEditReason = claim.allowEdit(player);
                        if(noEditReason == null)
                        {
                                //if he clicked on a corner, start resizing it
                                if((clickedBlock.getX() == claim.getLesserBoundaryCorner().getBlockX() || clickedBlock.getX() == claim.getGreaterBoundaryCorner().getBlockX()) && (clickedBlock.getZ() == claim.getLesserBoundaryCorner().getBlockZ() || clickedBlock.getZ() == claim.getGreaterBoundaryCorner().getBlockZ()))
                                {
                                        playerData.claimResizing = claim;
                                        playerData.lastShovelLocation = clickedBlock.getLocation();
                                        player.sendMessage("Resizing claim.  Use your shovel again at the new location for this corner.");
                                }
                                //if he didn't click on a corner and is in subdivision mode, he's creating a new subdivision
                                else if(playerData.shovelMode == ShovelMode.Subdivide)
                                {
                                        //if it's the first click, he's trying to start a new subdivision
                                        if(playerData.lastShovelLocation == null)
                                        {						
                                                //if the clicked claim was a subdivision, tell him he can't start a new subdivision here
                                                if(claim.parent != null)
                                                {
                                                        GriefPrevention.sendMessage(player, TextMode.Err, "You can't create a subdivision here because it would overlap another subdivision.  Consider /abandonclaim to delete it, or use your shovel at a corner to resize it.");							
                                                }
                                                //otherwise start a new subdivision
                                                else
                                                {
                                                        GriefPrevention.sendMessage(player, TextMode.Instr, "Subdivision corner set!  Use your shovel at the location for the opposite corner of this new subdivision.");
                                                        playerData.lastShovelLocation = clickedBlock.getLocation();
                                                        playerData.claimSubdividing = claim;
                                                }
                                        }
                                        //otherwise, he's trying to finish creating a subdivision by setting the other boundary corner
                                        else
                                        {
                                                //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
                                                if(!playerData.lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
                                                {
                                                        playerData.lastShovelLocation = null;
                                                        this.onPlayerInteract(event);
                                                        return;
                                                }
                                                //try to create a new claim (will return null if this subdivision overlaps another)
                                                CreateClaimResult result = this.dataStore.createClaim(
                                                        player.getWorld(), 
                                                        playerData.lastShovelLocation.getBlockX(), clickedBlock.getX(), 
                                                        playerData.lastShovelLocation.getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, 
                                                        playerData.lastShovelLocation.getBlockZ(), clickedBlock.getZ(), 
                                                        "--subdivision--",  //owner name is not used for subdivisions
                                                        playerData.claimSubdividing);
                                                //if it didn't succeed, tell the player why
                                                if(!result.succeeded)
                                                {
                                                        GriefPrevention.sendMessage(player, TextMode.Err, "Your selected area overlaps another subdivision.");
                                                        Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim);
                                                        Visualization.Apply(player, visualization);
                                                        return;
                                                }
                                                //otherwise, advise him on the /trust command and show him his new subdivision
                                                else
                                                {					
                                                        GriefPrevention.sendMessage(player, TextMode.Success, "Subdivision created!  Use /trust to share it with friends.");
                                                        Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim);
                                                        Visualization.Apply(player, visualization);
                                                        playerData.lastShovelLocation = null;
                                                        playerData.claimSubdividing = null;
                                                }
                                        }
                                }
                                //otherwise tell him he can't create a claim here, and show him the existing claim
                                //also advise him to consider /abandonclaim or resizing the existing claim
                                else
                                {						
                                        GriefPrevention.sendMessage(player, TextMode.Err, "You can't create a claim here because it would overlap your other claim.  Use /abandonclaim to delete it, or use your shovel at a corner to resize it.");
                                        Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.Claim);
                                        Visualization.Apply(player, visualization);
                                }
                        }
                        //otherwise tell the player he can't claim here because it's someone else's claim, and show him the claim
                        else
                        {
                                GriefPrevention.sendMessage(player, TextMode.Err, "You can't create a claim here because it would overlap " + claim.getOwnerName() + "'s claim.");
                                Visualization visualization = Visualization.FromClaim(claim, clickedBlock.getY(), VisualizationType.ErrorClaim);
                                Visualization.Apply(player, visualization);						
                        }
                        return;
                }
                //otherwise, the player isn't in an existing claim!
                //if he hasn't already start a claim with a previous shovel action
                Location lastShovelLocation = playerData.lastShovelLocation;
                if(lastShovelLocation == null)
                {
                        //if claims are not enabled in this world and it's not an administrative claim, display an error message and stop
                        if(!GriefPrevention.instance.claimsEnabledForWorld(player.getWorld()) && playerData.shovelMode != ShovelMode.Admin)
                        {
                                GriefPrevention.sendMessage(player, TextMode.Err, "Land claims are disabled in this world.");
                                return;
                        }
                        //remember it, and start him on the new claim
                        playerData.lastShovelLocation = clickedBlock.getLocation();
                        GriefPrevention.sendMessage(player, TextMode.Instr, "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away.");
                }
                //otherwise, he's trying to finish creating a claim by setting the other boundary corner
                else
                {
                        //if last shovel location was in a different world, assume the player is starting the create-claim workflow over
                        if(!lastShovelLocation.getWorld().equals(clickedBlock.getWorld()))
                        {
                                playerData.lastShovelLocation = null;
                                this.onPlayerInteract(event);
                                return;
                        }
                        //apply minimum claim dimensions rule
                        int newClaimWidth = Math.abs(playerData.lastShovelLocation.getBlockX() - clickedBlock.getX()) + 1;
                        int newClaimHeight = Math.abs(playerData.lastShovelLocation.getBlockZ() - clickedBlock.getZ()) + 1;
                        if(playerData.shovelMode != ShovelMode.Admin && (newClaimWidth < GriefPrevention.instance.config_claims_minSize || newClaimHeight < GriefPrevention.instance.config_claims_minSize))
                        {
                                GriefPrevention.sendMessage(player, TextMode.Err, "This claim would be too small.  Any claim must be at least " + GriefPrevention.instance.config_claims_minSize + " x " + GriefPrevention.instance.config_claims_minSize + ".");
                                return;
                        }
                        //if not an administrative claim, verify the player has enough claim blocks for this new claim
                        if(playerData.shovelMode != ShovelMode.Admin)
                        {					
                                int newClaimArea = newClaimWidth * newClaimHeight; 
                                int remainingBlocks = playerData.getRemainingClaimBlocks();
                                if(newClaimArea > remainingBlocks)
                                {
                                        GriefPrevention.sendMessage(player, TextMode.Err, "You don't have enough blocks to claim that entire area.  You need " + (newClaimArea - remainingBlocks) + " more blocks.");
                                        GriefPrevention.sendMessage(player, TextMode.Instr, "To delete another claim and free up some blocks, use /AbandonClaim.");
                                        return;
                                }
                        }					
                        else
                        {
                                playerName = "";
                        }
                        //try to create a new claim (will return null if this claim overlaps another)
                        CreateClaimResult result = this.dataStore.createClaim(
                                player.getWorld(), 
                                lastShovelLocation.getBlockX(), clickedBlock.getX(), 
                                lastShovelLocation.getBlockY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, clickedBlock.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance, 
                                lastShovelLocation.getBlockZ(), clickedBlock.getZ(), 
                                playerName,
                                null);
                        //if it didn't succeed, tell the player why
                        if(!result.succeeded)
                        {
                                GriefPrevention.sendMessage(player, TextMode.Err, "Your selected area overlaps an existing claim.");
                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.ErrorClaim);
                                Visualization.Apply(player, visualization);
                                return;
                        }
                        //otherwise, advise him on the /trust command and show him his new claim
                        else
                        {					
                                GriefPrevention.sendMessage(player, TextMode.Success, "Claim created!  Use /trust to share it with friends.");
                                Visualization visualization = Visualization.FromClaim(result.claim, clickedBlock.getY(), VisualizationType.Claim);
                                Visualization.Apply(player, visualization);
                                playerData.lastShovelLocation = null;
                        }
                }
        }

	//when a player interacts with the world
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	void onPlayerInteract(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		
		//determine target block.  FEATURE: shovel and string can be used from a distance away
		Block clickedBlock = event.getClickedBlock();
                Action action = event.getAction();
		
		//if no block, stop here
		if(clickedBlock == null)
		{
			return;
		}
		
		Material clickedBlockType = clickedBlock.getType();

                //deny ignition of TNT with flint and steel without build rights
                if (clickedBlockType == Material.TNT && action == Action.RIGHT_CLICK_BLOCK && player.getItemInHand().getType() == Material.FLINT_AND_STEEL) {
                        String noBuildReason = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
                        if(noBuildReason != null)
                        {
                                event.setCancelled(true);
                                GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                        }
                }

                //deny changing noteblocks in claims without build rights
                if (clickedBlockType == Material.NOTE_BLOCK && action == Action.RIGHT_CLICK_BLOCK) {
			Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, null);
			if(claim != null)
			{
				String noBuildReason = claim.allowBuild(player);
				if(noBuildReason != null)
				{
					event.setCancelled(true);
					GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
				}
			}
                }
		
		//apply rules for buttons and switches
		if (GriefPrevention.instance.config_claims_preventButtonsSwitches && (clickedBlockType == null || clickedBlockType == Material.STONE_BUTTON || clickedBlockType == Material.LEVER))
		{
			Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, null);
			if(claim != null)
			{
				String noAccessReason = claim.allowAccess(player);
				if(noAccessReason != null)
				{
					event.setCancelled(true);
					GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
				}
			}			
		}

                //apply access rules for doors
                else if (GriefPrevention.instance.config_claims_lockDoors && (clickedBlockType == Material.TRAP_DOOR || clickedBlockType == Material.WOODEN_DOOR || clickedBlockType == Material.FENCE_GATE)) {
                        PlayerData data = this.dataStore.getPlayerData(player.getName());
                        Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, data.lastClaim);
                        if (claim != null) {
                                String noAccessReason = claim.allowAccess(player);
                                if (noAccessReason != null) {
                                        event.setCancelled(true);
                                        GriefPrevention.sendMessage(player, TextMode.Err, noAccessReason);
                                }
                        }
                }
		
		//otherwise apply rules for containers and crafting blocks
		else if (GriefPrevention.instance.config_claims_preventTheft && (action == Action.RIGHT_CLICK_BLOCK && (clickedBlock.getState() instanceof InventoryHolder || clickedBlockType == Material.BREWING_STAND || clickedBlockType == Material.JUKEBOX || clickedBlockType == Material.ANVIL || clickedBlockType ==  Material.BEACON))) {
			//otherwise check permissions for the claim the player is in
			Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, null);
			if(claim != null)
			{
				String noContainersReason = claim.allowContainers(player);
				if(noContainersReason != null)
				{
					event.setCancelled(true);
					GriefPrevention.sendMessage(player, TextMode.Err, noContainersReason);
				}
			}
		}
		
		// apply rule for players trampling tilled soil back to dirt (works only with build permission)
		//NOTE: that this event applies only to players.  monsters and animals can still trample.
		else if(action == Action.PHYSICAL && clickedBlockType == Material.SOIL)
		{
                        if (GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation()) != null) {
                                event.setCancelled(true);
                        }
		}

                // deny punching the dragon egg without trust
                else if (clickedBlockType == Material.DRAGON_EGG && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
                        String denyMessage = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
                        if (denyMessage != null) {
                                event.setCancelled(true);
                                GriefPrevention.sendMessage(player, TextMode.Err, denyMessage);
                                return;
                        }
                }
		
		//otherwise handle right click (shovel, stick, bonemeal)
		else
		{
			//ignore all actions except right-click on a block or in the air
			if(action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
			
			//what's the player holding?
			Material materialInHand = player.getItemInHand().getType();		
			
			//if it's bonemeal, check for build permission (ink sac == bone meal, must be a Bukkit bug?)
			if(materialInHand == Material.INK_SACK)
			{
				String noBuildReason = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
				if(noBuildReason != null)
				{
					GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
					event.setCancelled(true);
				}
				
				return;
			}
			
			//if it's a spawn egg or minecart and this is a creative world, apply special rules
			else if((materialInHand == Material.MONSTER_EGG || materialInHand == Material.MINECART || materialInHand == Material.POWERED_MINECART || materialInHand == Material.STORAGE_MINECART) && GriefPrevention.instance.creativeRulesApply(clickedBlock.getLocation()))
			{
				//player needs build permission at this location
				String noBuildReason = GriefPrevention.instance.allowBuild(player, clickedBlock.getLocation());
				if(noBuildReason != null)
				{
					GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
					event.setCancelled(true);
					return;
				}
			
				//enforce limit on total number of entities in this claim
				PlayerData playerData = this.dataStore.getPlayerData(player.getName());
				Claim claim = this.dataStore.getClaimAt(clickedBlock.getLocation(), false, playerData.lastClaim);
				if(claim == null) return;
				
				String noEntitiesReason = claim.allowMoreEntities();
				if(noEntitiesReason != null)
				{
					GriefPrevention.sendMessage(player, TextMode.Err, noEntitiesReason);
					event.setCancelled(true);
					return;
				}
				
				return;
			}
                }
	}	
}
