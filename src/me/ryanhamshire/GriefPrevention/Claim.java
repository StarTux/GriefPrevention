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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

//represents a player claim
//creating an instance doesn't make an effective claim
//only claims which have been added to the datastore have any effect
public class Claim
{
	//two locations, which together define the boundaries of the claim
	//note that the upper Y value is always ignored, because claims ALWAYS extend up to the sky
	//IF MODIFIED, THE CLAIM DATA FILE'S NAME WILL CHANGE.  ANY MODIFICATIONS MUST BE HANDLED VERY CAREFULLY
	Location lesserBoundaryCorner;
	Location greaterBoundaryCorner;
	
	//modification date.  this comes from the file timestamp during load, and is updated with runtime changes
	public Date modifiedDate;
	
	//ownername.  for admin claims, this is the empty string
	//use getOwnerName() to get a friendly name (will be "an administrator" for admin claims)
	public String ownerName;
	
	//list of players who (beyond the claim owner) have permission to grant permissions in this claim
	public ArrayList<String> managers = new ArrayList<String>();
	
	//permissions for this claim, see ClaimPermission class
	private HashMap<String, ClaimPermission> playerNameToClaimPermissionMap = new HashMap<String, ClaimPermission>();
	
	//whether or not this claim is in the data store
	//if a claim instance isn't in the data store, it isn't "active" - players can't interract with it 
	//why keep this?  so that claims which have been removed from the data store can be correctly 
	//ignored even though they may have references floating around
	public boolean inDataStore = false;
	
	//parent claim
	//only used for claim subdivisions.  top level claims have null here
	public Claim parent = null;
	
	//children (subdivisions)
	//note subdivisions themselves never have children
	public ArrayList<Claim> children = new ArrayList<Claim>();
	
	//whether or not this is an administrative claim
	//administrative claims are created and maintained by players with the griefprevention.adminclaims permission.
	public boolean isAdminClaim()
	{
		return this.ownerName.isEmpty();
	}
	
	//basic constructor, just notes the creation time
	//see above declarations for other defaults
	Claim()
	{
		this.modifiedDate = Calendar.getInstance().getTime();
	}
	
	//main constructor.  note that only creating a claim instance does nothing - a claim must be added to the data store to be effective
	Claim(Location lesserBoundaryCorner, Location greaterBoundaryCorner, String ownerName, String [] builderNames, String [] containerNames, String [] accessorNames, String [] managerNames)
	{
		//modification date
		this.modifiedDate = Calendar.getInstance().getTime();
		
		//store corners
		this.lesserBoundaryCorner = lesserBoundaryCorner;
		this.greaterBoundaryCorner = greaterBoundaryCorner;
		
		//owner
		this.ownerName = ownerName;
		
		//other permissions
		for(int i = 0; i < builderNames.length; i++)
		{
			String name = builderNames[i];
			if(name != null && !name.isEmpty())
			{
				this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Build);
			}
		}
		
		for(int i = 0; i < containerNames.length; i++)
		{
			String name = containerNames[i];
			if(name != null && !name.isEmpty())
			{
				this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Inventory);
			}
		}
		
		for(int i = 0; i < accessorNames.length; i++)
		{
			String name = accessorNames[i];
			if(name != null && !name.isEmpty())
			{
				this.playerNameToClaimPermissionMap.put(name, ClaimPermission.Access);
			}
		}
		
		for(int i = 0; i < managerNames.length; i++)
		{
			String name = managerNames[i];
			if(name != null && !name.isEmpty())
			{
				this.managers.add(name);
			}
		}
	}
	
	//measurements.  all measurements are in blocks
	public int getArea()
	{
		int claimWidth = this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;
		int claimHeight = this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;
		
		return claimWidth * claimHeight;		
	}
	
	public int getWidth()
	{
		return this.greaterBoundaryCorner.getBlockX() - this.lesserBoundaryCorner.getBlockX() + 1;		
	}
	
	public int getHeight()
	{
		return this.greaterBoundaryCorner.getBlockZ() - this.lesserBoundaryCorner.getBlockZ() + 1;		
	}
	
	//distance check for claims, distance in this case is a band around the outside of the claim rather then euclidean distance
	public boolean isNear(Location location, int howNear)
	{
		Claim claim = new Claim
			(new Location(this.lesserBoundaryCorner.getWorld(), this.lesserBoundaryCorner.getBlockX() - howNear, this.lesserBoundaryCorner.getBlockY(), this.lesserBoundaryCorner.getBlockZ() - howNear),
			 new Location(this.greaterBoundaryCorner.getWorld(), this.greaterBoundaryCorner.getBlockX() + howNear, this.greaterBoundaryCorner.getBlockY(), this.greaterBoundaryCorner.getBlockZ() + howNear),
			 "", new String[] {}, new String[] {}, new String[] {}, new String[] {});
		
		return claim.contains(location, false, true);
	}
	
	//permissions.  note administrative "public" claims have different rules than other claims
	//all of these return NULL when a player has permission, or a String error message when the player doesn't have permission
	public String allowEdit(Player player)
	{
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";
		
		//special cases...
		
		//admin claims need adminclaims permission only.
		if(this.isAdminClaim())
		{
			if(player.hasPermission("griefprevention.adminclaims")) return null;
		}
		
		//anyone with deleteclaims permission can modify non-admin claims at any time
		else
		{
			if(player.hasPermission("griefprevention.deleteclaims")) return null;
		}

                //owners can do whatever
                if(this.ownerName.equals(player.getName()))
                {
                        return null;
                }

		//permission inheritance for subdivisions
		if(this.parent != null)
			return this.parent.allowBuild(player);
		
		//error message if all else fails
		return "Only " + this.getOwnerName() + " can modify this claim.";
	}
	
	//build permission check
	public String allowBuild(Player player)
	{
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";
		
		//admin claims can always be modified by admins, no exceptions
		if(this.isAdminClaim())
		{
			if(player.hasPermission("griefprevention.adminclaims")) return null;
		}
		
		//owners can make changes, or admins with ignore claims mode enabled
		if(this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) return null;
		
		//anyone with explicit build permission can make changes
		ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get(player.getName().toLowerCase());
		if(ClaimPermission.Build == permissionLevel) return null;
		
		//also everyone is a member of the "public", so check for public permission
		permissionLevel = this.playerNameToClaimPermissionMap.get("public");
		if(ClaimPermission.Build == permissionLevel) return null;
		
		//subdivision permission inheritance
		if(this.parent != null)
			return this.parent.allowBuild(player);
		
		//failure message for all other cases
		return "You don't have " + this.getOwnerName() + "'s permission to build here.";
	}
	
	//break permission check
	public String allowBreak(Player player, Material material)
	{
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";
		
		//build rules apply
		return this.allowBuild(player);		
	}
	
	//access permission check
	public String allowAccess(Player player)
	{
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";
		
		//everyone always has access to admin claims
		if(this.isAdminClaim()) return null;
		
		//claim owner and admins in ignoreclaims mode have access
		if(this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) return null;
		
		//look for explicit individual access, inventory, or build permission
		ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get(player.getName().toLowerCase());
		if(ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel || ClaimPermission.Access == permissionLevel) return null;
		
		//also check for public permission
		permissionLevel = this.playerNameToClaimPermissionMap.get("public");
		if(ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel || ClaimPermission.Access == permissionLevel) return null;
		
		//permission inheritance for subdivisions
		if(this.parent != null)
			return this.parent.allowAccess(player);
		
		//catch-all error message for all other cases
		return "You don't have " + this.getOwnerName() + "'s permission to use that.";
	}
	
	//inventory permission check
	public String allowContainers(Player player)
	{		
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";
		
		//containers are always accessible in admin claims
		if(this.isAdminClaim()) return null;
		
		//owner and administrators in ignoreclaims mode have access
		if(this.ownerName.equals(player.getName()) || GriefPrevention.instance.dataStore.getPlayerData(player.getName()).ignoreClaims) return null;
		
		//check for explicit individual container or build permission 
		ClaimPermission permissionLevel = this.playerNameToClaimPermissionMap.get(player.getName().toLowerCase());
		if(ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel) return null;
		
		//check for public container or build permission
		permissionLevel = this.playerNameToClaimPermissionMap.get("public");
		if(ClaimPermission.Build == permissionLevel || ClaimPermission.Inventory == permissionLevel) return null;
		
		//permission inheritance for subdivisions
		if(this.parent != null)
			return this.parent.allowContainers(player);
		
		//error message for all other cases
		return "You don't have " + this.getOwnerName() + "'s permission to use that.";
	}
	
	//grant permission check, relatively simple
	public String allowGrantPermission(Player player)
	{
		//if we don't know who's asking, always say no (i've been told some mods can make this happen somehow)
		if(player == null) return "";

		//anyone who can modify the claim, or who's explicitly in the managers (/PermissionTrust) list can do this
		if(this.allowEdit(player) == null || this.managers.contains(player.getName())) return null;
		
		//permission inheritance for subdivisions
		if(this.parent != null)
			return this.parent.allowGrantPermission(player);
		
		//generic error message
		return "You don't have " + this.getOwnerName() + "'s permission to grant permission here.";
	}
	
	//grants a permission for a player or the public
	public void setPermission(String playerName, ClaimPermission permissionLevel)
	{
		this.playerNameToClaimPermissionMap.put(playerName.toLowerCase(),  permissionLevel);
	}
	
	//revokes a permission for a player or the public
	public void dropPermission(String playerName)
	{
		this.playerNameToClaimPermissionMap.remove(playerName.toLowerCase());
	}
	
	//clears all permissions (except owner of course)
	public void clearPermissions()
	{
		this.playerNameToClaimPermissionMap.clear();
	}
	
	//gets ALL permissions
	//useful for  making copies of permissions during a claim resize and listing all permissions in a claim
	public void getPermissions(ArrayList<String> builders, ArrayList<String> containers, ArrayList<String> accessors, ArrayList<String> managers)
	{
		//loop through all the entries in the hash map
		Iterator<Map.Entry<String, ClaimPermission>> mappingsIterator = this.playerNameToClaimPermissionMap.entrySet().iterator(); 
		while(mappingsIterator.hasNext())
		{
			Map.Entry<String, ClaimPermission> entry = mappingsIterator.next();
			
			//build up a list for each permission level
			if(entry.getValue() == ClaimPermission.Build)
			{
				builders.add(entry.getKey());
			}
			else if(entry.getValue() == ClaimPermission.Inventory)
			{
				containers.add(entry.getKey());
			}
			else
			{
				accessors.add(entry.getKey());
			}			
		}
		
		//managers are handled a little differently
		for(int i = 0; i < this.managers.size(); i++)
		{
			managers.add(this.managers.get(i));
		}
	}
	
	//returns a copy of the location representing lower x, y, z limits
	public Location getLesserBoundaryCorner()
	{
		return this.lesserBoundaryCorner.clone();
	}
	
	//returns a copy of the location representing upper x, y, z limits
	//NOTE: remember upper Y will always be ignored, all claims always extend to the sky
	public Location getGreaterBoundaryCorner()
	{
		return this.greaterBoundaryCorner.clone();
	}
	
	//returns a friendly owner name (for admin claims, returns "an administrator" as the owner)
	public String getOwnerName()
	{
		if(this.parent != null)
			return this.parent.getOwnerName();
		
		if(this.ownerName.length() == 0)
			return "an administrator";
		
		return this.ownerName;
	}	
	
	//whether or not a location is in a claim
	//ignoreHeight = true means location UNDER the claim will return TRUE
	//excludeSubdivisions = true means that locations inside subdivisions of the claim will return FALSE
	public boolean contains(Location location, boolean ignoreHeight, boolean excludeSubdivisions)
	{
		//not in the same world implies false
		if(!location.getWorld().equals(this.lesserBoundaryCorner.getWorld())) return false;
		
		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();
		
		//main check
		boolean inClaim = (ignoreHeight || y >= this.lesserBoundaryCorner.getBlockY()) &&
				x >= this.lesserBoundaryCorner.getBlockX() &&
				x <= this.greaterBoundaryCorner.getBlockX() &&
				z >= this.lesserBoundaryCorner.getBlockZ() &&
				z <= this.greaterBoundaryCorner.getBlockZ();
				
		if(!inClaim) return false;
				
	    //additional check for subdivisions
		//you're only in a subdivision when you're also in its parent claim
		//NOTE: if a player creates subdivions then resizes the parent claim, it's possible that
		//a subdivision can reach outside of its parent's boundaries.  so this check is important!
		if(this.parent != null)
	    {
	    	return this.parent.contains(location, ignoreHeight, false);
	    }
		
		//code to exclude subdivisions in this check
		else if(excludeSubdivisions)
		{
			//search all subdivisions to see if the location is in any of them
			for(int i = 0; i < this.children.size(); i++)
			{
				//if we find such a subdivision, return false
				if(this.children.get(i).contains(location, ignoreHeight, true))
				{
					return false;
				}
			}
		}
		
		//otherwise yes
		return true;				
	}
	
	//whether or not two claims overlap
	//used internally to prevent overlaps when creating claims
	boolean overlaps(Claim otherClaim)
	{
		//NOTE:  if trying to understand this makes your head hurt, don't feel bad - it hurts mine too.  
		//try drawing pictures to visualize test cases.
		
		if(!this.lesserBoundaryCorner.getWorld().equals(otherClaim.getLesserBoundaryCorner().getWorld())) return false;
		
		//first, check the corners of this claim aren't inside any existing claims
		if(otherClaim.contains(this.lesserBoundaryCorner, true, false)) return true;
		if(otherClaim.contains(this.greaterBoundaryCorner, true, false)) return true;
		if(otherClaim.contains(new Location(this.lesserBoundaryCorner.getWorld(), this.lesserBoundaryCorner.getBlockX(), 0, this.greaterBoundaryCorner.getBlockZ()), true, false)) return true;
		if(otherClaim.contains(new Location(this.lesserBoundaryCorner.getWorld(), this.greaterBoundaryCorner.getBlockX(), 0, this.lesserBoundaryCorner.getBlockZ()), true, false)) return true;
		
		//verify that no claim's lesser boundary point is inside this new claim, to cover the "existing claim is entirely inside new claim" case
		if(this.contains(otherClaim.getLesserBoundaryCorner(), true, false)) return true;
		
		//verify this claim doesn't band across an existing claim, either horizontally or vertically		
		if(	this.getLesserBoundaryCorner().getBlockZ() <= otherClaim.getGreaterBoundaryCorner().getBlockZ() && 
			this.getLesserBoundaryCorner().getBlockZ() >= otherClaim.getLesserBoundaryCorner().getBlockZ() && 
			this.getLesserBoundaryCorner().getBlockX() < otherClaim.getLesserBoundaryCorner().getBlockX() &&
			this.getGreaterBoundaryCorner().getBlockX() > otherClaim.getGreaterBoundaryCorner().getBlockX() )
			return true;
		
		if(	this.getGreaterBoundaryCorner().getBlockZ() <= otherClaim.getGreaterBoundaryCorner().getBlockZ() && 
			this.getGreaterBoundaryCorner().getBlockZ() >= otherClaim.getLesserBoundaryCorner().getBlockZ() && 
			this.getLesserBoundaryCorner().getBlockX() < otherClaim.getLesserBoundaryCorner().getBlockX() &&
			this.getGreaterBoundaryCorner().getBlockX() > otherClaim.getGreaterBoundaryCorner().getBlockX() )
			return true;
		
		if(	this.getLesserBoundaryCorner().getBlockX() <= otherClaim.getGreaterBoundaryCorner().getBlockX() && 
			this.getLesserBoundaryCorner().getBlockX() >= otherClaim.getLesserBoundaryCorner().getBlockX() && 
			this.getLesserBoundaryCorner().getBlockZ() < otherClaim.getLesserBoundaryCorner().getBlockZ() &&
			this.getGreaterBoundaryCorner().getBlockZ() > otherClaim.getGreaterBoundaryCorner().getBlockZ() )
			return true;
			
		if(	this.getGreaterBoundaryCorner().getBlockX() <= otherClaim.getGreaterBoundaryCorner().getBlockX() && 
			this.getGreaterBoundaryCorner().getBlockX() >= otherClaim.getLesserBoundaryCorner().getBlockX() && 
			this.getLesserBoundaryCorner().getBlockZ() < otherClaim.getLesserBoundaryCorner().getBlockZ() &&
			this.getGreaterBoundaryCorner().getBlockZ() > otherClaim.getGreaterBoundaryCorner().getBlockZ() )
			return true;
		
		return false;
	}
	
	//whether more entities may be added to a claim
	public String allowMoreEntities()
	{
		if(this.parent != null) return this.parent.allowMoreEntities();
		
		//this rule only applies to creative mode worlds
		if(!GriefPrevention.instance.creativeRulesApply(this.getLesserBoundaryCorner())) return null;
		
		//determine maximum allowable entity count, based on claim size
		int maxEntities = this.getArea() / 50;		
		if(maxEntities == 0) return "This claim isn't big enough for that.  Try enlarging it.";
		
		//count current entities (ignoring players)
		Chunk lesserChunk = this.getLesserBoundaryCorner().getChunk();
		Chunk greaterChunk = this.getGreaterBoundaryCorner().getChunk();
		
		int totalEntities = 0;
		for(int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++)
			for(int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++)
			{
				Chunk chunk = lesserChunk.getWorld().getChunkAt(x, z);
				Entity [] entities = chunk.getEntities();
				for(int i = 0; i < entities.length; i++)
				{
					Entity entity = entities[i];
					if(!(entity instanceof Player) && this.contains(entity.getLocation(), false, false)) totalEntities++;
				}
			}

		if(totalEntities > maxEntities) return "This claim has too many entities already.  Try enlarging the claim or removing some animals, monsters, or minecarts.";
		
		return null;
	}
	
	//implements a strict ordering of claims, used to keep the claims collection sorted for faster searching
	boolean greaterThan(Claim otherClaim)
	{
		Location thisCorner = this.getLesserBoundaryCorner();
		Location otherCorner = otherClaim.getLesserBoundaryCorner();
		
		if(thisCorner.getBlockX() > otherCorner.getBlockX()) return true;
		
		if(thisCorner.getBlockX() < otherCorner.getBlockX()) return false;
		
		if(thisCorner.getBlockZ() > otherCorner.getBlockZ()) return true;
		
		if(thisCorner.getBlockZ() < otherCorner.getBlockZ()) return false;
		
		return thisCorner.getWorld().getName().compareTo(otherCorner.getWorld().getName()) < 0;
	}
}
