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
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

import org.bukkit.Location;

//holds all of GriefPrevention's player-tied data
public class PlayerData 
{
	//the player's claims
	public Vector<Claim> claims = new Vector<Claim>();
	
	//how many claim blocks the player has earned via play time
	public int accruedClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
	
	//where this player was the last time we checked on him for earning claim blocks
	public Location lastAfkCheckLocation = null;
	
	//how many claim blocks the player has been gifted by admins, or purchased via economy integration 
	public int bonusClaimBlocks = 0;
	
	//what "mode" the shovel is in determines what it will do when it's used
	public ShovelMode shovelMode = ShovelMode.Basic;
	
	//radius for restore nature fill mode
	int fillRadius = 0;
	
	//last place the player used the shovel, useful in creating and resizing claims, 
	//because the player must use the shovel twice in those instances
	public Location lastShovelLocation = null;	
	
	//the claim this player is currently resizing
	public Claim claimResizing = null;
	
	//the claim this player is currently subdividing
	public Claim claimSubdividing = null;
	
	//the timestamp for the last time the player used /trapped
	public Date lastTrappedUsage;
	
	//whether or not the player has a pending /trapped rescue
	public boolean pendingTrapped = false;
	
	//last place the player damaged a chest
	public Location lastChestDamageLocation = null;
	
	//spam
	public Date lastLogin;							//when the player last logged into the server
	
	//last logout timestamp, default to long enough to trigger a join message, see player join event
	public long lastLogout = Calendar.getInstance().getTimeInMillis() - GriefPrevention.NOTIFICATION_SECONDS * 2000;
	
	//visualization
	public Visualization currentVisualization = null;
	
	//ignore claims mode
	public boolean ignoreClaims = false;
	
	//the last claim this player was in, that we know of
	public Claim lastClaim = null;
	
	//safety confirmation for deleting multi-subdivision claims
	public boolean warnedAboutMajorDeletion = false;

	PlayerData()
	{
		//default last login date value to a year ago to ensure a brand new player can log in
		//see login cooldown feature, PlayerEventHandler.onPlayerLogin()
		//if the player successfully logs in, this value will be overwritten with the current date and time 
		Calendar lastYear = Calendar.getInstance();
		lastYear.add(Calendar.YEAR, -1);
		this.lastLogin = lastYear.getTime();		
		this.lastTrappedUsage = lastYear.getTime();
	}
	
	//the number of claim blocks a player has available for claiming land
	public int getRemainingClaimBlocks()
	{
		int remainingBlocks = this.accruedClaimBlocks + this.bonusClaimBlocks;
		for(int i = 0; i < this.claims.size(); i++)
		{
			Claim claim = this.claims.get(i);
			remainingBlocks -= claim.getArea();
		}
		
		return remainingBlocks;
	}
}