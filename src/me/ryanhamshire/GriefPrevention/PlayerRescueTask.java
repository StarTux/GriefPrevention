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

import java.util.Calendar;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

//tries to rescue a trapped player from a claim where he doesn't have permission to save himself
//related to the /trapped slash command
//this does run in the main thread, so it's okay to make non-thread-safe calls
class PlayerRescueTask implements Runnable 
{
	//original location where /trapped was used
	private Location location;
	
	//player data
	private Player player;
	
	public PlayerRescueTask(Player player, Location location)
	{
		this.player = player;
		this.location = location;		
	}
	
	@Override
	public void run()
	{
		//if he logged out, don't do anything
		if(!player.isOnline()) return;
		
		//he no longer has a pending /trapped slash command, so he can try to use it again now
		PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getName());
		playerData.pendingTrapped = false;
		
		//if the player moved three or more blocks from where he used /trapped, admonish him and don't save him
		if(player.getLocation().distance(this.location) > 3)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, "You moved! Rescue cancelled.");
			return;
		}
		
		//log entry, in case admins want to investigate the "trap"
		GriefPrevention.addLogEntry("Helped trapped player " + player.getName() + " at " + GriefPrevention.getfriendlyLocationString(this.location));

                if (!player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL)).isEmpty()) {
                        player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.ENDER_PEARL));
                }
                GriefPrevention.sendMessage(player, ChatColor.DARK_AQUA, "It's dangerous to go alone. Take this.");
                GriefPrevention.sendMessage(player, ChatColor.DARK_AQUA, "You got the " + ChatColor.AQUA + "Ender Pearl" + ChatColor.DARK_AQUA + ".");
		
		//timestamp this successful save so that he can't use /trapped again for a while		
		playerData.lastTrappedUsage = Calendar.getInstance().getTime();		
	}
}
