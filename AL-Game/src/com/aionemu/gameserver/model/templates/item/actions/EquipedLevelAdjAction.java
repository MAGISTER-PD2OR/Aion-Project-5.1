/*
 * This file is part of Encom. **ENCOM FUCK OTHER SVN**
 *
 *  Encom is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Encom is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with Encom.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.model.templates.item.actions;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INVENTORY_UPDATE_ITEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EquipedLevelAdjAction")
public class EquipedLevelAdjAction extends AbstractItemAction {
	
	@XmlAttribute(name="count")
	protected Integer reduceCount;

	@Override
	public boolean canAct(Player player, Item parentItem, Item targetItem) {
		if (parentItem == null || targetItem == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_EQUIPLEVEL_ADJ_NO_TARGET_ITEM);
            return false;
		} if (!targetItem.isArchDaevaItem()) {
            PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_EQUIPLEVEL_ADJ_CANNOT(parentItem.getNameId()));
            return false;
        } if (targetItem.isPacked()) {
            PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_EQUIPLEVEL_ADJ_WRONG_PACK);
            return false;
        } if (targetItem.hasRetuning()) {
            PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_EQUIPLEVEL_ADJ_NEED_IDENTIFY);
            return false;
        }
		return true;
	}

	@Override
	public void act(final Player player, final Item parentItem, final Item targetItem) {
		player.getController().cancelUseItem();
		PacketSendUtility.broadcastPacketAndReceive(player, new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parentItem.getObjectId(), parentItem.getItemId(), 3000, 0, 0));
		final ItemUseObserver observer = new ItemUseObserver() {

			@Override
			public void abort() {
				player.getController().cancelTask(TaskId.ITEM_USE);
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_CANCELED(new DescriptionId(parentItem.getItemTemplate().getNameId())));
				PacketSendUtility.broadcastPacket(player, new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parentItem.getObjectId(), parentItem.getItemTemplate().getTemplateId(), 0, 2, 0), true);
				player.getObserveController().removeObserver(this);
			}
		};
		player.getObserveController().attach(observer);
		player.getController().addTask(TaskId.ITEM_USE, ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				boolean succ = player.getInventory().decreaseByObjectId(parentItem.getObjectId().intValue(), 1);
				if (succ) {
					player.getObserveController().removeObserver(observer);
					PacketSendUtility.broadcastPacket(player, new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), parentItem.getObjectId(), parentItem.getItemId(), 0, 1, 0), true);
					targetItem.setReductionLevel(targetItem.getReductionLevel() + 1);
					PacketSendUtility.sendPacket(player, new SM_INVENTORY_UPDATE_ITEM(player, targetItem));
					player.getObserveController().removeObserver(observer);
					if (targetItem.isEquipped()) {
						player.getGameStats().updateStatsVisually();
					}
					
					ItemPacketService.updateItemAfterInfoChange(player, targetItem);
					
					if (targetItem.isEquipped()) {
						player.getEquipment().setPersistentState(PersistentState.UPDATE_REQUIRED);
					} else {
						player.getInventory().setPersistentState(PersistentState.UPDATE_REQUIRED);
					}
				}
			}
		}, 3000));
	}
    
}