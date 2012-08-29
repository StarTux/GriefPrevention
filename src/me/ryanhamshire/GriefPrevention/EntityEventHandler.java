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
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.painting.PaintingBreakByEntityEvent;
import org.bukkit.event.painting.PaintingBreakEvent;
import org.bukkit.event.painting.PaintingPlaceEvent;

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
	
	//when a painting is broken
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onPaintingBreak(PaintingBreakEvent event)
        {
                //FEATURE: claimed paintings are protected from breakage
		
		//only allow players to break paintings, not anything else (like water and explosions)
		if(!(event instanceof PaintingBreakByEntityEvent))
                {
                        event.setCancelled(true);
                        return;
                }
        
                PaintingBreakByEntityEvent entityEvent = (PaintingBreakByEntityEvent)event;
        
                //who is removing it?
		Entity remover = entityEvent.getRemover();
        
		//again, making sure the breaker is a player
		if(!(remover instanceof Player))
                {
                        event.setCancelled(true);
                        return;
                }
		
		//if the player doesn't have build permission, don't allow the breakage
		Player playerRemover = (Player)entityEvent.getRemover();
                String noBuildReason = GriefPrevention.instance.allowBuild(playerRemover, event.getPainting().getLocation());
                if(noBuildReason != null)
                {
                        event.setCancelled(true);
                        GriefPrevention.sendMessage(playerRemover, TextMode.Err, noBuildReason);
                }
        }
	
	//when a painting is placed...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onPaintingPlace(PaintingPlaceEvent event)
	{
		//FEATURE: similar to above, placing a painting requires build permission in the claim
	
		//if the player doesn't have permission, don't allow the placement
		String noBuildReason = GriefPrevention.instance.allowBuild(event.getPlayer(), event.getPainting().getLocation());
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

        @EventHandler(priority = EventPriority.LOWEST)
        public void onEntityTarget(EntityTargetEvent event) {
                if (event.isCancelled()) return;
                if (event.getEntity().getType() == EntityType.CREEPER && event.getTarget() != null) {
                        Creeper creeper = (Creeper)event.getEntity();
                        if (event.getTarget() instanceof LivingEntity) {
                                creeper.setTarget((LivingEntity)event.getTarget());
                        }
                }
        }

        @EventHandler
        public void onEntityExplode(EntityExplodeEvent event) {
                if (event.isCancelled()) return;
                Entity entity = event.getEntity();
                Claim claim = this.dataStore.getClaimAt(entity.getLocation(), true, null);

                // Deny creeper explosions unless target can build
                if (entity.getType() == EntityType.CREEPER && claim != null) {
                        Creeper creeper = (Creeper)entity;
                        LivingEntity target = creeper.getTarget();
                        if (target == null || !(target instanceof Player)) {
                                event.blockList().clear();
                        } else if (claim.allowBuild((Player)target) != null) {
                                event.blockList().clear();
                        }
                }
        }
}
