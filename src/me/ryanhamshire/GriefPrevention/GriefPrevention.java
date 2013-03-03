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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class GriefPrevention extends JavaPlugin {
	//for convenience, a reference to the instance of this plugin
	public static GriefPrevention instance;
	
	//this handles data storage, like player and region data
	public DataStore dataStore;
	
	//configuration variables, loaded/saved from a config.yml
	public ArrayList<World> config_claims_enabledWorlds;			//list of worlds where players can create GriefPrevention claims
	public ArrayList<World> config_claims_enabledCreativeWorlds;	//list of worlds where additional creative mode anti-grief rules apply
	
	public boolean config_claims_preventTheft;						//whether containers and crafting blocks are protectable
	public boolean config_claims_preventButtonsSwitches;			//whether buttons and switches are protectable
	
	public int config_claims_initialBlocks;							//the number of claim blocks a new player starts with
	public int config_claims_blocksAccruedPerHour;					//how many additional blocks players get each hour of play (can be zero)
	public int config_claims_maxAccruedBlocks;						//the limit on accrued blocks (over time).  doesn't limit purchased or admin-gifted blocks 
	public int config_claims_maxDepth;								//limit on how deep claims can go
	public int config_claims_expirationDays;						//how many days of inactivity before a player loses his claims
	
	public int config_claims_automaticClaimsForNewPlayersRadius;	//how big automatic new player claims (when they place a chest) should be.  0 to disable
	public boolean config_claims_creationRequiresPermission;		//whether creating claims with the shovel requires a permission
	public int config_claims_claimsExtendIntoGroundDistance;		//how far below the shoveled block a new claim will reach
	public int config_claims_minSize;								//minimum width and height for non-admin claims
        public boolean config_claims_firePlacementRequiresTrust; //players can only place fire (or lava) in claims with trust if set to true
        public boolean config_claims_fireCannotCrossClaimBorders; //prevent fire spread (or lava flow) from crossing claim borders
        public boolean config_claims_lockDoors; //protect all doors by accesstrust
	
	public int config_claims_trappedCooldownHours;					//number of hours between uses of the /trapped command
	
        public double config_economy_claimBlocksPurchaseCost;			//cost to purchase a claim block.  set to zero to disable purchase.
	public double config_economy_claimBlocksSellValue;				//return on a sold claim block.  set to zero to disable sale.
	
	//reference to the economy plugin, if economy integration is enabled
	public static Economy economy = null;					
	
        //how long to wait before deciding a player is staying online or staying offline, for notication messages
	public static final int NOTIFICATION_SECONDS = 20;
	
	//adds a server log entry
	public static void addLogEntry(String entry)
	{
		GriefPrevention.instance.getLogger().info("GriefPrevention: " + entry);
	}
	
	//initializes well...   everything
	public void onEnable()
	{ 		
		instance = this;
		addLogEntry("Grief Prevention enabled.");
		
		//load the config if it exists
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
		
		//read configuration settings (note defaults)
		
		//default for claims worlds list
		ArrayList<String> defaultClaimsWorldNames = new ArrayList<String>();
		List<World> worlds = this.getServer().getWorlds(); 
		for(int i = 0; i < worlds.size(); i++)
		{
			defaultClaimsWorldNames.add(worlds.get(i).getName());
		}
		
		//get claims world names from the config file
		List<String> claimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.Worlds");
		if(claimsEnabledWorldNames == null || claimsEnabledWorldNames.size() == 0)
		{			
			claimsEnabledWorldNames = defaultClaimsWorldNames;
		}
		
		//validate that list
		this.config_claims_enabledWorlds = new ArrayList<World>();
		for(int i = 0; i < claimsEnabledWorldNames.size(); i++)
		{
			String worldName = claimsEnabledWorldNames.get(i);
			World world = this.getServer().getWorld(worldName);
			if(world == null)
			{
				addLogEntry("Error: Claims Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
			}
			else
			{
				this.config_claims_enabledWorlds.add(world);
			}
		}
		
		//default creative claim world names
		List<String> defaultCreativeWorldNames = new ArrayList<String>();
		
		//if default game mode for the server is creative, creative rules will apply to all worlds unless the config specifies otherwise
		if(this.getServer().getDefaultGameMode() == GameMode.CREATIVE)
		{
			for(int i = 0; i < defaultClaimsWorldNames.size(); i++)
			{
				defaultCreativeWorldNames.add(defaultClaimsWorldNames.get(i));
			}			
		}
		
		//get creative world names from the config file
		List<String> creativeClaimsEnabledWorldNames = config.getStringList("GriefPrevention.Claims.CreativeRulesWorlds");
		if(creativeClaimsEnabledWorldNames == null || creativeClaimsEnabledWorldNames.size() == 0)
		{			
			creativeClaimsEnabledWorldNames = defaultCreativeWorldNames;
		}
		
		//validate that list
		this.config_claims_enabledCreativeWorlds = new ArrayList<World>();
		for(int i = 0; i < creativeClaimsEnabledWorldNames.size(); i++)
		{
			String worldName = creativeClaimsEnabledWorldNames.get(i);
			World world = this.getServer().getWorld(worldName);
			if(world == null)
			{
				addLogEntry("Error: Claims Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
			}
			else
			{
				this.config_claims_enabledCreativeWorlds.add(world);
			}
		}
		
		this.config_claims_preventTheft = config.getBoolean("GriefPrevention.Claims.PreventTheft", true);
		this.config_claims_preventButtonsSwitches = config.getBoolean("GriefPrevention.Claims.PreventButtonsSwitches", true);		
		this.config_claims_initialBlocks = config.getInt("GriefPrevention.Claims.InitialBlocks", 100);
		this.config_claims_blocksAccruedPerHour = config.getInt("GriefPrevention.Claims.BlocksAccruedPerHour", 100);
		this.config_claims_maxAccruedBlocks = config.getInt("GriefPrevention.Claims.MaxAccruedBlocks", 80000);
		this.config_claims_automaticClaimsForNewPlayersRadius = config.getInt("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", 4);
		this.config_claims_claimsExtendIntoGroundDistance = config.getInt("GriefPrevention.Claims.ExtendIntoGroundDistance", 5);
		this.config_claims_creationRequiresPermission = config.getBoolean("GriefPrevention.Claims.CreationRequiresPermission", false);
		this.config_claims_minSize = config.getInt("GriefPrevention.Claims.MinimumSize", 10);
		this.config_claims_maxDepth = config.getInt("GriefPrevention.Claims.MaximumDepth", 0);
		this.config_claims_expirationDays = config.getInt("GriefPrevention.Claims.IdleLimitDays", 0);
		this.config_claims_trappedCooldownHours = config.getInt("GriefPrevention.Claims.TrappedCommandCooldownHours", 8);
                this.config_claims_firePlacementRequiresTrust = config.getBoolean("GriefPrevention.Claims.FirePlacementRequiresTrust", true);
                this.config_claims_fireCannotCrossClaimBorders = config.getBoolean("GriefPrevention.Claims.FireCannotCrossClaimBorders", true);
                this.config_claims_lockDoors = config.getBoolean("GriefPrevention.Claims.LockDoors", false);
		
		this.config_economy_claimBlocksPurchaseCost = config.getDouble("GriefPrevention.Economy.ClaimBlocksPurchaseCost", 0);
		this.config_economy_claimBlocksSellValue = config.getDouble("GriefPrevention.Economy.ClaimBlocksSellValue", 0);

		config.set("GriefPrevention.Claims.Worlds", claimsEnabledWorldNames);
		config.set("GriefPrevention.Claims.CreativeRulesWorlds", creativeClaimsEnabledWorldNames);
		config.set("GriefPrevention.Claims.PreventTheft", this.config_claims_preventTheft);
		config.set("GriefPrevention.Claims.PreventButtonsSwitches", this.config_claims_preventButtonsSwitches);
		config.set("GriefPrevention.Claims.InitialBlocks", this.config_claims_initialBlocks);
		config.set("GriefPrevention.Claims.BlocksAccruedPerHour", this.config_claims_blocksAccruedPerHour);
		config.set("GriefPrevention.Claims.MaxAccruedBlocks", this.config_claims_maxAccruedBlocks);
		config.set("GriefPrevention.Claims.AutomaticNewPlayerClaimsRadius", this.config_claims_automaticClaimsForNewPlayersRadius);
		config.set("GriefPrevention.Claims.ExtendIntoGroundDistance", this.config_claims_claimsExtendIntoGroundDistance);
		config.set("GriefPrevention.Claims.CreationRequiresPermission", this.config_claims_creationRequiresPermission);
		config.set("GriefPrevention.Claims.MinimumSize", this.config_claims_minSize);
		config.set("GriefPrevention.Claims.MaximumDepth", this.config_claims_maxDepth);
		config.set("GriefPrevention.Claims.IdleLimitDays", this.config_claims_expirationDays);
		config.set("GriefPrevention.Claims.TrappedCommandCooldownHours", this.config_claims_trappedCooldownHours);
                config.set("GriefPrevention.Claims.FirePlacementRequiresTrust", config_claims_firePlacementRequiresTrust);
                config.set("GriefPrevention.Claims.FireCannotCrossClaimBorders", config_claims_fireCannotCrossClaimBorders);
                config.set("GriefPrevention.Claims.LockDoors", config_claims_lockDoors);
                
		config.set("GriefPrevention.Economy.ClaimBlocksPurchaseCost", this.config_economy_claimBlocksPurchaseCost);
		config.set("GriefPrevention.Economy.ClaimBlocksSellValue", this.config_economy_claimBlocksSellValue);

		try
		{
			config.save(DataStore.configFilePath);
		}
		catch(IOException exception)
		{
			addLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
		
		//when datastore initializes, it loads player and claim data, and posts some stats to the log
		this.dataStore = new DataStore();
		
		//unless claim block accrual is disabled, start the recurring per 5 minute event to give claim blocks to online players
		//20L ~ 1 second
		if(this.config_claims_blocksAccruedPerHour > 0)
		{
			DeliverClaimBlocksTask task = new DeliverClaimBlocksTask();
			this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 20L * 60 * 5, 20L * 60 * 5);
		}
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		
		//player events
		PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
		pluginManager.registerEvents(playerEventHandler, this);
		
		//block events
		BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
		pluginManager.registerEvents(blockEventHandler, this);
				
		//entity events
		EntityEventHandler entityEventHandler = new EntityEventHandler(this.dataStore);
		pluginManager.registerEvents(entityEventHandler, this);
		
		//if economy is enabled
		if(this.config_economy_claimBlocksPurchaseCost > 0 || this.config_economy_claimBlocksSellValue > 0) {
			//try to load Vault
			GriefPrevention.addLogEntry("GriefPrevention requires Vault for economy integration.");
			GriefPrevention.addLogEntry("Attempting to load Vault...");
			RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
			GriefPrevention.addLogEntry("Vault loaded successfully!");
			
			//ask Vault to hook into an economy plugin
			GriefPrevention.addLogEntry("Looking for a Vault-compatible economy plugin...");
			if (economyProvider != null) {
                                GriefPrevention.economy = economyProvider.getProvider();
	            
                                //on success, display success message
				if(GriefPrevention.economy != null) {
                                        GriefPrevention.addLogEntry("Hooked into economy: " + GriefPrevention.economy.getName() + ".");  
                                        GriefPrevention.addLogEntry("Ready to buy/sell claim blocks!");
                                } else {
                                        //otherwise error message
                                        GriefPrevention.addLogEntry("ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero.");
                                }	            
                        } else {
                                //another error case
				GriefPrevention.addLogEntry("ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero.");
			}
		}		
	}
	
	//handles slash commands
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		
		Player player = null;
		if (sender instanceof Player) 
		{
			player = (Player) sender;
		}

                //claims
                if (cmd.getName().equalsIgnoreCase("claims")) {
                        Claim claim = dataStore.getClaimAt(player.getLocation(), true, null);
                        if (claim == null) {
                                player.sendMessage("" + ChatColor.RED + "You must stand inside a claim.");
                                return true;
                        }
                        player.sendMessage("Depth: " + claim.lesserBoundaryCorner.getBlockY());
                        return true;
                }
		
		//abandonclaim
		if(cmd.getName().equalsIgnoreCase("abandonclaim") && player != null)
		{
			return this.abandonClaimHandler(player, false);
		}		
		
		//abandontoplevelclaim
		if(cmd.getName().equalsIgnoreCase("abandontoplevelclaim") && player != null)
		{
			return this.abandonClaimHandler(player, true);
		}
		
		//ignoreclaims
		if(cmd.getName().equalsIgnoreCase("ignoreclaims") && player != null)
		{
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			
			playerData.ignoreClaims = !playerData.ignoreClaims;
			
			//toggle ignore claims mode on or off
			if(!playerData.ignoreClaims)
			{
				GriefPrevention.sendMessage(player, TextMode.Success, "Now respecting claims.");
			}
			else
			{
				GriefPrevention.sendMessage(player, TextMode.Success, "Now ignoring claims.");
			}
			
			return true;
		}
		
		//abandonallclaims
		else if(cmd.getName().equalsIgnoreCase("abandonallclaims") && player != null)
		{
			if(args.length != 0) return false;
			
			if(creativeRulesApply(player.getLocation()))
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Creative mode claims can't be abandoned.");
				return true;
			}
			
			//count claims
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			int originalClaimCount = playerData.claims.size();
			
			//check count
			if(originalClaimCount == 0)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You haven't claimed any land.");
				return true;
			}
			
			//delete them
			this.dataStore.deleteClaimsForPlayer(player.getName(), false);
			
			//inform the player
			int remainingBlocks = playerData.getRemainingClaimBlocks();
			GriefPrevention.sendMessage(player, TextMode.Success, "Claims abandoned.  You now have " + String.valueOf(remainingBlocks) + " available claim blocks.");
			
			//revert any current visualization
			Visualization.Revert(player);
			
			return true;
		}
		
		//trust <player>
		else if(cmd.getName().equalsIgnoreCase("trust") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			//most trust commands use this helper method, it keeps them consistent
			this.handleTrustCommand(player, ClaimPermission.Build, args[0]);
			
			return true;
		}
		
		//transferclaim <player>
		else if(cmd.getName().equalsIgnoreCase("transferclaim") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			//check additional permission
			if(!player.hasPermission("griefprevention.adminclaims"))
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "That command requires the administrative claims permission.");
				return true;
			}
			
			//which claim is the user in?
			Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
			if(claim == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Instr, "There's no claim here.  Stand in the administrative claim you want to transfer.");
				return true;
			}
			
			OfflinePlayer targetPlayer = this.resolvePlayer(args[0]);
			if(targetPlayer == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Player not found.");
				return true;
			}
			
			//change ownerhsip
			try
			{
				this.dataStore.changeClaimOwner(claim, targetPlayer.getName());
			}
			catch(Exception e)
			{
				GriefPrevention.sendMessage(player, TextMode.Instr, "Only top level claims (not subdivisions) may be transferred.  Stand outside of the subdivision and try again.");
				return true;
			}
			
			//confirm
			GriefPrevention.sendMessage(player, TextMode.Success, "Claim transferred.");
			GriefPrevention.addLogEntry(player.getName() + " transferred a claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " to " + targetPlayer.getName() + ".");
			
			return true;
		}
		
		//trustlist
		else if(cmd.getName().equalsIgnoreCase("trustlist") && player != null)
		{
			Claim claim = this.dataStore.getClaimAt(player.getLocation(), true, null);
			
			//if no claim here, error message
			if(claim == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Stand inside the claim you're curious about.");
				return true;
			}
			
			//if no permission to manage permissions, error message
			String errorMessage = claim.allowGrantPermission(player);
			if(errorMessage != null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, errorMessage);
				return true;
			}
			
			//otherwise build a list of explicit permissions by permission level
			//and send that to the player
			ArrayList<String> builders = new ArrayList<String>();
			ArrayList<String> containers = new ArrayList<String>();
			ArrayList<String> accessors = new ArrayList<String>();
			ArrayList<String> managers = new ArrayList<String>();
			claim.getPermissions(builders, containers, accessors, managers);
			
                        player.sendMessage(ChatColor.DARK_AQUA + "Owned by " + ChatColor.AQUA + claim.getOwnerName() + ChatColor.DARK_AQUA + ". Trusted are:");
                        StringBuilder permissions;

			permissions = new StringBuilder();
			permissions.append(ChatColor.AQUA).append("Build").append(ChatColor.DARK_AQUA).append(": ");
                        for(int i = 0; i < builders.size(); i++) permissions.append(builders.get(i) + " ");
			player.sendMessage(permissions.toString());

			permissions = new StringBuilder();
			permissions.append(ChatColor.AQUA).append("Containers").append(ChatColor.DARK_AQUA).append(": ");
                        for(int i = 0; i < containers.size(); i++) permissions.append(containers.get(i) + " ");
			player.sendMessage(permissions.toString());

			permissions = new StringBuilder();
			permissions.append(ChatColor.AQUA).append("Access").append(ChatColor.DARK_AQUA).append(": ");
                        for(int i = 0; i < accessors.size(); i++) permissions.append(accessors.get(i) + " ");
			player.sendMessage(permissions.toString());

			permissions = new StringBuilder();
			permissions.append(ChatColor.AQUA).append("Permission").append(ChatColor.DARK_AQUA).append(": ");
                        for(int i = 0; i < managers.size(); i++) permissions.append(managers.get(i) + " ");
			player.sendMessage(permissions.toString());

			return true;
		}
		
		//untrust <player>
		else if(cmd.getName().equalsIgnoreCase("untrust") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			//determine which claim the player is standing in
			Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
			
			//determine whether a single player or clearing permissions entirely
			boolean clearPermissions = false;
			OfflinePlayer otherPlayer = null;
			if(args[0].equals("all"))				
			{
				if(claim == null || claim.allowEdit(player) == null)
				{
					clearPermissions = true;
				}
				else
				{
					GriefPrevention.sendMessage(player, TextMode.Err, "Only the claim owner can clear all permissions.");
					return true;
				}
			}
			
			else
			{
				//validate player argument
				otherPlayer = this.resolvePlayer(args[0]);
				if(!clearPermissions && otherPlayer == null && !args[0].equals("public"))
				{
					GriefPrevention.sendMessage(player, TextMode.Err, "Player not found.");
					return true;
				}
				
				//correct to proper casing
				if(otherPlayer != null)
					args[0] = otherPlayer.getName();
			}
			
			//if no claim here, apply changes to all his claims
			if(claim == null)
			{
				PlayerData playerData = this.dataStore.getPlayerData(player.getName());
				for(int i = 0; i < playerData.claims.size(); i++)
				{
					claim = playerData.claims.get(i);
					
					//if untrusting "all" drop all permissions
					if(clearPermissions)
					{	
						claim.clearPermissions();
					}
					
					//otherwise drop individual permissions
					else
					{
						claim.dropPermission(args[0]);
						claim.managers.remove(args[0]);
					}
					
					//save changes
					this.dataStore.saveClaim(claim);
				}
				
				//beautify for output
				if(args[0].equals("public"))
				{
					args[0] = "the public";
				}
				
				//confirmation message
				if(!clearPermissions)
				{
					GriefPrevention.sendMessage(player, TextMode.Success, "Revoked " + args[0] + "'s access to ALL your claims.  To set permissions for a single claim, stand inside it.");
				}
				else
				{
					GriefPrevention.sendMessage(player, TextMode.Success, "Cleared permissions in ALL your claims.  To set permissions for a single claim, stand inside it.");
				}
			}			
			
			//otherwise, apply changes to only this claim
			else if(claim.allowGrantPermission(player) != null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You don't have " + claim.getOwnerName() + "'s permission to manage permissions here.");
			}
			else
			{
				//if clearing all
				if(clearPermissions)
				{
					claim.clearPermissions();
					GriefPrevention.sendMessage(player, TextMode.Success, "Cleared permissions in this claim.  To set permission for ALL your claims, stand outside them.");
				}
				
				//otherwise individual permission drop
				else
				{
					claim.dropPermission(args[0]);
					if(claim.allowEdit(player) == null)
					{
						claim.managers.remove(args[0]);
						
						//beautify for output
						if(args[0].equals("public"))
						{
							args[0] = "the public";
						}
						
						GriefPrevention.sendMessage(player, TextMode.Success, "Revoked " + args[0] + "'s access to this claim.  To set permissions for a ALL your claims, stand outside them.");
					}
				}
				
				//save changes
				this.dataStore.saveClaim(claim);										
			}
			
			return true;
		}
		
		//accesstrust <player>
		else if(cmd.getName().equalsIgnoreCase("accesstrust") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			this.handleTrustCommand(player, ClaimPermission.Access, args[0]);
			
			return true;
		}
		
		//containertrust <player>
		else if(cmd.getName().equalsIgnoreCase("containertrust") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			this.handleTrustCommand(player, ClaimPermission.Inventory, args[0]);
			
			return true;
		}
		
		//permissiontrust <player>
		else if(cmd.getName().equalsIgnoreCase("permissiontrust") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			this.handleTrustCommand(player, null, args[0]);  //null indicates permissiontrust to the helper method
			
			return true;
		}
		
		//buyclaimblocks
		else if(cmd.getName().equalsIgnoreCase("buyclaimblocks") && player != null)
		{
			//if economy is disabled, don't do anything
			if(GriefPrevention.economy == null) return true;
			
			//if purchase disabled, send error message
			if(GriefPrevention.instance.config_economy_claimBlocksPurchaseCost == 0)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Claim blocks may only be sold, not purchased.");
				return true;
			}
			
			//if no parameter, just tell player cost per block and balance
			if(args.length != 1)
			{
				GriefPrevention.sendMessage(player, TextMode.Info, "Each claim block costs " + GriefPrevention.instance.config_economy_claimBlocksPurchaseCost + ".  Your balance is " + GriefPrevention.economy.getBalance(player.getName()) + ".");
				return false;
			}
			
			else
			{
				//determine max purchasable blocks
				PlayerData playerData = this.dataStore.getPlayerData(player.getName());
				int maxPurchasable = GriefPrevention.instance.config_claims_maxAccruedBlocks - playerData.accruedClaimBlocks;
				
				//if the player is at his max, tell him so
				if(maxPurchasable <= 0)
				{
					GriefPrevention.sendMessage(player, TextMode.Err, "You've reached your claim block limit.  You can't purchase more.");
					return true;
				}
				
				//try to parse number of blocks
				int blockCount;
				try
				{
					blockCount = Integer.parseInt(args[0]);
				}
				catch(NumberFormatException numberFormatException)
				{
					return false;  //causes usage to be displayed
				}
				
				//correct block count to max allowed
				if(blockCount > maxPurchasable)
				{
					blockCount = maxPurchasable;
				}
				
				//if the player can't afford his purchase, send error message
				double balance = economy.getBalance(player.getName());				
				double totalCost = blockCount * GriefPrevention.instance.config_economy_claimBlocksPurchaseCost;				
				if(totalCost > balance)
				{
					GriefPrevention.sendMessage(player, TextMode.Err, "You don't have enough money.  You need " + totalCost + ", but you only have " + balance + ".");
				}
				
				//otherwise carry out transaction
				else
				{
					//withdraw cost
					economy.withdrawPlayer(player.getName(), totalCost);
					
					//add blocks
					playerData.accruedClaimBlocks += blockCount;
					this.dataStore.savePlayerData(player.getName(), playerData);
					
					//inform player
					GriefPrevention.sendMessage(player, TextMode.Success, "Withdrew " + totalCost + " from your account.  You now have " + playerData.getRemainingClaimBlocks() + " available claim blocks.");
				}
				
				return true;
			}
		}
		
		//sellclaimblocks <amount> 
		else if(cmd.getName().equalsIgnoreCase("sellclaimblocks") && player != null)
		{
			//if economy is disabled, don't do anything
			if(GriefPrevention.economy == null) return true;
			
			//if disabled, error message
			if(GriefPrevention.instance.config_economy_claimBlocksSellValue == 0)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Claim blocks may only be purchased, not sold.");
				return true;
			}
			
			//load player data
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			int availableBlocks = playerData.getRemainingClaimBlocks();
			
			//if no amount provided, just tell player value per block sold, and how many he can sell
			if(args.length != 1)
			{
				GriefPrevention.sendMessage(player, TextMode.Info, "Each claim block is worth " + GriefPrevention.instance.config_economy_claimBlocksSellValue + ".  You have " + availableBlocks + " available for sale.");
				return false;
			}
						
			//parse number of blocks
			int blockCount;
			try
			{
				blockCount = Integer.parseInt(args[0]);
			}
			catch(NumberFormatException numberFormatException)
			{
				return false;  //causes usage to be displayed
			}
			
			//if he doesn't have enough blocks, tell him so
			if(blockCount > availableBlocks)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You don't have that many claim blocks available for sale.");
			}
			
			//otherwise carry out the transaction
			else
			{					
				//compute value and deposit it
				double totalValue = blockCount * GriefPrevention.instance.config_economy_claimBlocksSellValue;					
				economy.depositPlayer(player.getName(), totalValue);
				
				//subtract blocks
				playerData.accruedClaimBlocks -= blockCount;
				this.dataStore.savePlayerData(player.getName(), playerData);
				
				//inform player
				GriefPrevention.sendMessage(player, TextMode.Success, "Deposited " + totalValue + " in your account.  You now have " + playerData.getRemainingClaimBlocks() + " available claim blocks.");
			}
			
			return true;
		}		
		
		//adminclaims
		else if(cmd.getName().equalsIgnoreCase("adminclaims") && player != null)
		{
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			playerData.shovelMode = ShovelMode.Admin;
			GriefPrevention.sendMessage(player, TextMode.Success, "Administrative claims mode active.  Any claims created will be free and editable by other administrators.");
			
			return true;
		}
		
		//basicclaims
		else if(cmd.getName().equalsIgnoreCase("basicclaims") && player != null)
		{
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			playerData.shovelMode = ShovelMode.Basic;
			playerData.claimSubdividing = null;
			GriefPrevention.sendMessage(player, TextMode.Success, "Returned to basic claim creation mode.");
			
			return true;
		}
		
		//subdivideclaims
		else if(cmd.getName().equalsIgnoreCase("subdivideclaims") && player != null)
		{
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			playerData.shovelMode = ShovelMode.Subdivide;
			playerData.claimSubdividing = null;
			GriefPrevention.sendMessage(player, TextMode.Instr, "Subdivision mode.  Use your shovel to create subdivisions in your existing claims.  Use /basicclaims to exit.");
			
			return true;
		}
		
		//deleteclaim
		else if(cmd.getName().equalsIgnoreCase("deleteclaim") && player != null)
		{
			//determine which claim the player is standing in
			Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
			
			if(claim == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "There's no claim here.");
			}
			
			else 
			{
				//deleting an admin claim additionally requires the adminclaims permission
				if(!claim.isAdminClaim() || player.hasPermission("griefprevention.adminclaims"))
				{
					PlayerData playerData = this.dataStore.getPlayerData(player.getName());
					if(claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion)
					{
						GriefPrevention.sendMessage(player, TextMode.Warn, "This claim includes subdivisions.  If you're sure you want to delete it, use /DeleteClaim again.");
						playerData.warnedAboutMajorDeletion = true;
					}
					else
					{
						this.dataStore.deleteClaim(claim);
						GriefPrevention.sendMessage(player, TextMode.Success, "Claim deleted.");
						GriefPrevention.addLogEntry(player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
						
						//revert any current visualization
						Visualization.Revert(player);
						
						playerData.warnedAboutMajorDeletion = false;
					}
				}
				else
				{
					GriefPrevention.sendMessage(player, TextMode.Err, "You don't have permission to delete administrative claims.");
				}
			}

			return true;
		}
		
		//deleteallclaims <player>
		else if(cmd.getName().equalsIgnoreCase("deleteallclaims") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			//try to find that player
			OfflinePlayer otherPlayer = this.resolvePlayer(args[0]);
			if(otherPlayer == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Player not found.");
				return true;
			}
			
			//delete all that player's claims
			this.dataStore.deleteClaimsForPlayer(otherPlayer.getName(), true);
			
			GriefPrevention.sendMessage(player, TextMode.Success, "Deleted all of " + otherPlayer.getName() + "'s claims.");
			
			//revert any current visualization
			Visualization.Revert(player);
			
			return true;
		}
		
		//resetclaims <player>
		else if(cmd.getName().equalsIgnoreCase("resetclaims") && player != null)
		{
			//requires exactly one parameter, the other player's name
			if(args.length != 1) return false;
			
			//try to find that player
			OfflinePlayer otherPlayer = this.resolvePlayer(args[0]);
			if(otherPlayer == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "Player not found.");
				return true;
			}
			
			//delete all that player's claims
			this.dataStore.deleteClaimsForPlayer(otherPlayer.getName(), true);
                        //set claim blocks bank to zero
			PlayerData playerData = this.dataStore.getPlayerData(otherPlayer.getName());
			playerData.accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;                        
                        this.dataStore.savePlayerData(otherPlayer.getName(), playerData);

			GriefPrevention.sendMessage(player, TextMode.Success, "Deleted " + otherPlayer.getName() + "'s claims and set their block bank to the initial value.");
			
			//revert any current visualization
			Visualization.Revert(player);
			
			return true;
		}

                //deletealladminclaims
		else if(cmd.getName().equalsIgnoreCase("deletealladminclaims") && player != null)
		{
			if(!player.hasPermission("griefprevention.deleteclaims"))
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You don't have permission to delete claims.");
				return true;
			}
			
			//delete all admin claims
			this.dataStore.deleteClaimsForPlayer("", true);  //empty string for owner name indicates an administrative claim
			
			GriefPrevention.sendMessage(player, TextMode.Success, "Deleted all administrative claims.");
			
			//revert any current visualization
			Visualization.Revert(player);
			
			return true;
		}
		
		//adjustbonusclaimblocks <player> <amount>
		else if(cmd.getName().equalsIgnoreCase("adjustbonusclaimblocks"))
		{
			//requires exactly two parameters, the other player's name and the adjustment
			if(args.length != 2) return false;
			
			//find the specified player
			OfflinePlayer targetPlayer = this.resolvePlayer(args[0]);
			if(targetPlayer == null)
			{
				GriefPrevention.sendMessage(sender, TextMode.Err, "Player \"" + args[0] + "\" not found.");
				return true;
			}
			
			//parse the adjustment amount
			int adjustment;			
			try
			{
				adjustment = Integer.parseInt(args[1]);
			}
			catch(NumberFormatException numberFormatException)
			{
				return false;  //causes usage to be displayed
			}
			
			//give blocks to player
			PlayerData playerData = this.dataStore.getPlayerData(targetPlayer.getName());
			playerData.bonusClaimBlocks += adjustment;
			this.dataStore.savePlayerData(targetPlayer.getName(), playerData);
			
			GriefPrevention.sendMessage(sender, TextMode.Success, "Adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".  New total bonus blocks: " + playerData.bonusClaimBlocks + ".");
			GriefPrevention.addLogEntry(sender.getName() + " adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".");
			
			return true;			
		}
		
		//setblockbank <player> <amount>
		else if(cmd.getName().equalsIgnoreCase("setblockbank"))
		{
			//requires exactly two parameters, the other player's name and the adjustment
			if(args.length != 2) return false;
			
			//find the specified player
			OfflinePlayer targetPlayer = this.resolvePlayer(args[0]);
			if(targetPlayer == null)
			{
				GriefPrevention.sendMessage(sender, TextMode.Err, "Player \"" + args[0] + "\" not found.");
				return true;
			}
			
			//parse the adjustment amount
			int adjustment;			
			try
			{
				adjustment = Integer.parseInt(args[1]);
			}
			catch(NumberFormatException numberFormatException)
			{
				return false;  //causes usage to be displayed
			}
			
			//give blocks to player
			PlayerData playerData = this.dataStore.getPlayerData(targetPlayer.getName());
			playerData.accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
                        this.dataStore.savePlayerData(targetPlayer.getName(), playerData);
			
			GriefPrevention.sendMessage(sender, TextMode.Success, "Adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".  New total bonus blocks: " + playerData.bonusClaimBlocks + ".");
			GriefPrevention.addLogEntry(sender.getName() + " adjusted " + targetPlayer.getName() + "'s bonus claim blocks by " + adjustment + ".");
			
			return true;			
		}

                //trapped
		else if(cmd.getName().equalsIgnoreCase("trapped") && player != null)
		{
			//FEATURE: empower players who get "stuck" in an area where they don't have permission to build to save themselves
			
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			Claim claim = this.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
			
			//if another /trapped is pending, ignore this slash command
			if(playerData.pendingTrapped)
			{
				return true;
			}
			
			//if the player isn't in a claim or has permission to build, tell him to man up
			if(claim == null || claim.allowBuild(player) == null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You can build here. Save yourself.");
				return true;
			}

                        // if the player has ender pearls, he should be able to help himself
                        if (player.getInventory().contains(Material.ENDER_PEARL)) {
				GriefPrevention.sendMessage(player, TextMode.Err, "You have ender pearls. Use them to save yourself.");
				return true;
                        }

			//check cooldown
			long lastTrappedUsage = playerData.lastTrappedUsage.getTime();
			long nextTrappedUsage = lastTrappedUsage + 1000 * 60 * 60 * this.config_claims_trappedCooldownHours; 
			long now = Calendar.getInstance().getTimeInMillis();
			if(now < nextTrappedUsage)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You used /trapped within the last " + this.config_claims_trappedCooldownHours + " hours.  You have to wait about " + ((nextTrappedUsage - now) / (1000 * 60) + 1) + " more minutes before using it again.");
				return true;
			}
			
			//send instructions
			GriefPrevention.sendMessage(player, ChatColor.AQUA, "Help is on the way. Stay put for 10 seconds.");
			
			//create a task to rescue this player in a little while
			PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation());
			this.getServer().getScheduler().scheduleSyncDelayedTask(this, task, 200L);  //20L ~ 1 second
			
			return true;
		}
		
		return false; 
	}
	
	public static String getfriendlyLocationString(Location location) 
	{
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
	}

	private boolean abandonClaimHandler(Player player, boolean deleteTopLevelClaim) 
	{
		//which claim is being abandoned?
		Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
		if(claim == null)
		{
			GriefPrevention.sendMessage(player, TextMode.Instr, "Stand in the claim you want to delete, or consider /AbandonAllClaims.");
		}
		
		else if(this.creativeRulesApply(player.getLocation()))
		{
			GriefPrevention.sendMessage(player, TextMode.Err, "Creative-mode claims can't be abandoned.");
		}
		
		//verify ownership
		else if(claim.allowEdit(player) != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, "This isn't your claim.");
		}
		
		//warn if has children and we're not explicitly deleting a top level claim
		else if(claim.children.size() > 0 && !deleteTopLevelClaim)
		{
			GriefPrevention.sendMessage(player, TextMode.Instr, "To delete a subdivision, stand inside it.  Otherwise, use /AbandonTopLevelClaim to delete this claim and all subdivisions.");
			return true;
		}
		
		else
		{
			//delete it
			this.dataStore.deleteClaim(claim);
			
			//tell the player how many claim blocks he has left
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			int remainingBlocks = playerData.getRemainingClaimBlocks();
			GriefPrevention.sendMessage(player, TextMode.Success, "Claim abandoned.  You now have " + String.valueOf(remainingBlocks) + " available claim blocks.");
			
			//revert any current visualization
			Visualization.Revert(player);
		}
		
		return true;
		
	}

	//helper method keeps the trust commands consistent and eliminates duplicate code
	private void handleTrustCommand(Player player, ClaimPermission permissionLevel, String recipientName) 
	{
		//determine which claim the player is standing in
		Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
		
		//validate player argument
		OfflinePlayer otherPlayer = this.resolvePlayer(recipientName);
		if(otherPlayer == null && !recipientName.equals("public"))
		{
			GriefPrevention.sendMessage(player, TextMode.Err, "Player not found.");
			return;
		}
		
		if(otherPlayer != null)
		{
			recipientName = otherPlayer.getName();
		}
		else
		{
			recipientName = "public";
		}
		
		//determine which claims should be modified
		ArrayList<Claim> targetClaims = new ArrayList<Claim>();
		if(claim == null)
		{
			PlayerData playerData = this.dataStore.getPlayerData(player.getName());
			for(int i = 0; i < playerData.claims.size(); i++)
			{
				targetClaims.add(playerData.claims.get(i));
			}
		}
		else
		{
			//check permission here
			if(claim.allowGrantPermission(player) != null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, "You don't have " + claim.getOwnerName() + "'s permission to grant permissions here.");
				return;
			}
			
			//see if the player has the level of permission he's trying to grant
			String errorMessage = null;
			
			//permission level null indicates granting permission trust
			if(permissionLevel == null)
			{
				errorMessage = claim.allowEdit(player);
				if(errorMessage != null)
				{
					errorMessage = "Only " + claim.getOwnerName() + " can grant /PermissionTrust here."; 
				}
			}
			
			//otherwise just use the ClaimPermission enum values
			else
			{
				switch(permissionLevel)
				{
                                case Access:
                                        errorMessage = claim.allowAccess(player);
                                        break;
                                case Inventory:
                                        errorMessage = claim.allowContainers(player);
                                        break;
                                default:
                                        errorMessage = claim.allowBuild(player);					
				}
			}
			
			//error message for trying to grant a permission the player doesn't have
			if(errorMessage != null)
			{
				GriefPrevention.sendMessage(player, TextMode.Err, errorMessage + "  You can't grant a permission you don't have yourself.");
				return;
			}
			
			targetClaims.add(claim);
		}
		
		//if we didn't determine which claims to modify, tell the player to be specific
		if(targetClaims.size() == 0)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, "Stand inside the claim where you want to grant permission.");
			return;
		}
		
		//apply changes
		for(int i = 0; i < targetClaims.size(); i++)
		{
			Claim currentClaim = targetClaims.get(i);
			if(permissionLevel == null)
			{
				if(!currentClaim.managers.contains(recipientName))
				{
					currentClaim.managers.add(recipientName);
				}
			}
			else
			{				
				currentClaim.setPermission(recipientName, permissionLevel);
			}
			this.dataStore.saveClaim(currentClaim);
		}
		
		//notify player
		if(recipientName.equals("public")) recipientName = "the public";
		StringBuilder resultString = new StringBuilder();
		resultString.append("Granted " + recipientName + " ");
		if(permissionLevel == null)
		{
			resultString.append("manager status");
		}
		else if(permissionLevel == ClaimPermission.Build)
		{
			resultString.append("permission to build in");
		}		
		else if(permissionLevel == ClaimPermission.Access)
		{
			resultString.append("permission to use buttons and levers in");
		}
		else if(permissionLevel == ClaimPermission.Inventory)
		{
			resultString.append("permission to access containers in");
		}
		
		if(claim == null)
		{
			resultString.append(" ALL your claims.  To modify only one claim, stand inside it.");
		}
		else
		{
			resultString.append(" this claim.  To modify ALL your claims, stand outside them.");
		}
		
		GriefPrevention.sendMessage(player, TextMode.Success, resultString.toString());
	}

	//helper method to resolve a player by name
	private OfflinePlayer resolvePlayer(String name) 
	{
		//try online players first
		Player player = this.getServer().getPlayer(name);
		if(player != null) return player;
		
		//then search offline players
		OfflinePlayer [] offlinePlayers = this.getServer().getOfflinePlayers();
		for(int i = 0; i < offlinePlayers.length; i++)
		{
			if(offlinePlayers[i].getName().equalsIgnoreCase(name))
			{
				return offlinePlayers[i];
			}
		}
		
		//if none found, return null
		return null;
	}

	public void onDisable()
	{ 
		addLogEntry("GriefPrevention disabled.");
	}
	
	//checks whether players can create claims in a world
	public boolean claimsEnabledForWorld(World world)
	{
		return this.config_claims_enabledWorlds.contains(world);
	}
	
	//moves a player from the claim he's in to a nearby wilderness location
	public Location ejectPlayer(Player player)
	{
		//look for a suitable location
		Location candidateLocation = player.getLocation();
		while(true)
		{
			Claim claim = null;
			claim = GriefPrevention.instance.dataStore.getClaimAt(candidateLocation, false, null);
			
			//if there's a claim here, keep looking
			if(claim != null)
			{
				candidateLocation = new Location(claim.lesserBoundaryCorner.getWorld(), claim.lesserBoundaryCorner.getBlockX() - 1, claim.lesserBoundaryCorner.getBlockY(), claim.lesserBoundaryCorner.getBlockZ() - 1);
				continue;
			}
			
			//otherwise find a safe place to teleport the player
			else
			{
				//find a safe height, a couple of blocks above the surface
				GuaranteeChunkLoaded(candidateLocation);
				Block highestBlock = candidateLocation.getWorld().getHighestBlockAt(candidateLocation.getBlockX(), candidateLocation.getBlockZ());
				Location destination = new Location(highestBlock.getWorld(), highestBlock.getX(), highestBlock.getY() + 2, highestBlock.getZ());
				player.teleport(destination);			
				return destination;
			}			
		}
	}
	
	//ensures a piece of the managed world is loaded into server memory
	//(generates the chunk if necessary)
	private static void GuaranteeChunkLoaded(Location location)
	{
		Chunk chunk = location.getChunk();
		while(!chunk.isLoaded() || !chunk.load(true));
	}
	
	//sends a color-coded message to a player
	static void sendMessage(CommandSender sender, ChatColor color, String message)
	{
		sender.sendMessage(color + message);
	}
	
	//determines whether creative anti-grief rules apply at a location
	boolean creativeRulesApply(Location location)
	{
		return this.config_claims_enabledCreativeWorlds.contains(location.getWorld());
	}
	
	public String allowBuild(Player player, Location location)
	{
		PlayerData playerData = this.dataStore.getPlayerData(player.getName());
		Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
		
		//wilderness rules
		if(claim == null)
		{
			//no building in the wilderness in creative mode
			if(this.creativeRulesApply(location))
			{
				//exception: administrators in ignore claims mode
				if(playerData.ignoreClaims) return null;
				
				return "You can't build here.  Use the golden shovel to claim some land first.";
			}
			
			//but it's fine in survival mode
			else
			{
				//cache the claim for later reference
				playerData.lastClaim = claim;
			
				return null;
			}
		}
		
		//if not in the wilderness, then apply claim rules (permissions, etc)
		return claim.allowBuild(player);
	}
	
	public String allowBreak(Player player, Location location)
	{
		PlayerData playerData = this.dataStore.getPlayerData(player.getName());
		Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
		
		//wilderness rules
		if(claim == null)
		{
			//no building in the wilderness in creative mode
			if(this.creativeRulesApply(location))
			{
				//exception: administrators in ignore claims mode
				if(playerData.ignoreClaims) return null;
				
				return "You can't build here.  Use the golden shovel to claim some land first.";
			}
			
			//but it's fine in survival mode
			else
			{
				//cache the claim for later reference
				playerData.lastClaim = claim;
			
				return null;
			}
		}
		
		//if not in the wilderness, then apply claim rules (permissions, etc)
		return claim.allowBreak(player, location.getBlock().getType());
	}
}
