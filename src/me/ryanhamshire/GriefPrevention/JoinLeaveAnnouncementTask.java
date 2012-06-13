/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 StarTux

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

import java.lang.Runnable;
import org.bukkit.entity.Player;

public class JoinLeaveAnnouncementTask implements Runnable {
        private Player player;
        private String message;
        private boolean joined;

        public JoinLeaveAnnouncementTask(Player player, String message, boolean joined) {
                this.player = player;
                this.message = message;
                this.joined = joined;
        }
        
        public void run() {
                if (!player.isOnline()) return;
                GriefPrevention.instance.getServer().broadcastMessage(message);
        }
}