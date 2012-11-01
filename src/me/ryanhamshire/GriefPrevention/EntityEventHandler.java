/*
  GriefPrevention Server Plugin for Minecraft
  Copyright (C) 2012 Ryan Hamshire

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.
  n
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package me.ryanhamshire.GriefPrevention;

import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

//handles events related to entities
class EntityEventHandler implements Listener
{
	//convenience reference for the singleton datastore
	private DataStore dataStore;
	
	public EntityEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
	}
	
	//when an entity picks up an item
	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityPickup(EntityChangeBlockEvent event)
	{
		//FEATURE: endermen don't steal claimed blocks
		
		//if its an enderman
		if(event.getEntity() instanceof Enderman)
		{
			//and the block is claimed
			if(this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null) != null)
			{
				//he doesn't get to steal it
				event.setCancelled(true);
			}
		}
	}
	
	//when a Hanging is broken
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onHangingBreak(HangingBreakEvent event)
        {
                //FEATURE: claimed Hangings are protected from breakage
		
		//only allow players to break Hangings, not anything else (like water and explosions)
                //TODO: Do something smarter
		if(!(event instanceof HangingBreakByEntityEvent))
                {
                        event.setCancelled(true);
                        return;
                }
        
                HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent)event;
        
                //who is removing it?
		Entity remover = entityEvent.getRemover();
                Player playerRemover = null;
        
                if (remover instanceof Player) {
                        playerRemover = (Player)entityEvent.getRemover();
                } else if (remover instanceof Creature) {
                        LivingEntity target = ((Creature)remover).getTarget();
                        if (target == null) {
                                return;
                        } else if (target instanceof Player) {
                                playerRemover = (Player)target;
                        } else {
                                return;
                        }
                } else if (remover instanceof Projectile) {
                        LivingEntity shooter = ((Projectile)remover).getShooter();
                        if (shooter instanceof Player) {
                                playerRemover = (Player)shooter;
                        } else if (shooter instanceof Creature) {
                                LivingEntity target = ((Creature)shooter).getTarget();
                                if (target == null) {
                                        return;
                                } else if (target instanceof Player) {
                                        playerRemover = (Player)target;
                                } else {
                                        return;
                                }
                        } else {
                                return;
                        }
                } else {
                        return;
                }
		
		//if the player doesn't have build permission, don't allow the breakage
                String noBuildReason = GriefPrevention.instance.allowBuild(playerRemover, event.getEntity().getLocation());
                if(noBuildReason != null)
                {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(playerRemover, TextMode.Err, noBuildReason);
                }
        }
	
	//when a Hanging is placed...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onHangingPlace(HangingPlaceEvent event)
	{
		//FEATURE: similar to above, placing a Hanging requires build permission in the claim
	
		//if the player doesn't have permission, don't allow the placement
		String noBuildReason = GriefPrevention.instance.allowBuild(event.getPlayer(), event.getEntity().getLocation());
                if(noBuildReason != null)
                {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, noBuildReason);
                }		
	}
	
	//when an entity is damaged
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityDamage (EntityDamageEvent event)
	{
		//only actually interested in entities damaging entities (ignoring environmental damage)
		if(!(event instanceof EntityDamageByEntityEvent)) return;
		
		EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;
		
		//determine which player is attacking, if any
		Player attacker = null;
		Entity damageSource = subEvent.getDamager();
		if(damageSource instanceof Player)
		{
			attacker = (Player)damageSource;
		}
		else if(damageSource instanceof Arrow)
		{
			Arrow arrow = (Arrow)damageSource;
			if(arrow.getShooter() instanceof Player)
			{
				attacker = (Player)arrow.getShooter();
			}
		}
		else if(damageSource instanceof ThrownPotion)
		{
			ThrownPotion potion = (ThrownPotion)damageSource;
			if(potion.getShooter() instanceof Player)
			{
				attacker = (Player)potion.getShooter();
			}
		}
		
		//FEATURE: protect claimed animals, boats, minecarts
		//NOTE: animals can be lead with wheat, vehicles can be pushed around.
		//so unless precautions are taken by the owner, a resourceful thief might find ways to steal anyway
		
		//if theft protection is enabled
		if(GriefPrevention.instance.config_claims_preventTheft && event instanceof EntityDamageByEntityEvent)
		{
                        // Any player can do with their own pets whatever they want.
                        if (subEvent.getEntity() instanceof Tameable) {
                                Tameable tameable = (Tameable)subEvent.getEntity();
                                if (tameable.isTamed() && tameable.getOwner().equals(attacker)) return;
                        }
			//if the entity is an animal or a vehicle
			if (subEvent.getEntity() instanceof Animals || subEvent.getEntity() instanceof Vehicle)
			{
				Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, null);
				
				//if it's claimed
				if(claim != null)
				{
					//the player damaging the entity must have permission
					if (attacker != null) {
						String noContainersReason = claim.allowContainers(attacker);
						if(noContainersReason != null)
						{
							event.setCancelled(true);
							GriefPrevention.sendMessage(attacker, TextMode.Err, "That belongs to " + claim.getOwnerName() + ".");
						}
					}
				}
			}
		}
	}

        @EventHandler
        public void onEntityExplode(EntityExplodeEvent event) {
                if (event.isCancelled()) return;

                // Deny creeper explosions unless target can build
                if (event.getEntity() instanceof Creeper) {
                        Creeper creeper = (Creeper)event.getEntity();
                        if (creeper.getTarget() != null & creeper.getTarget() instanceof Player) {
                                Player player = (Player)creeper.getTarget();
                                PlayerData playerData = this.dataStore.getPlayerData(player.getName());
                                Claim lastClaim = playerData.lastClaim;
                                for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext(); ) {
                                        Block block = iter.next();
                                        Claim claim = dataStore.getClaimAt(block.getLocation(), false, lastClaim);
                                        if (claim != null) {
                                                lastClaim = claim;
                                                if (claim.allowBuild(player) != null) {
                                                        iter.remove();
                                                }
                                        }
                                }
                        }
                }
        }
}
