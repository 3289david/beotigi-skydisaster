package com.beotigi.skydisaster.util;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * HUD(게이지/카운트다운/보스바)는 절대 안 쓴다. 하지만 완전히 무정보로 두면 그냥 답답할 뿐이다.
 * 큰 사건이 벌어지기 직전/직후에 짧은 분위기 있는 채팅 한 줄 정도는 남긴다 -
 * 정확한 초 단위 카운트다운이 아니라 "어? 뭔가 이상한데" 느낌의 대사에 가깝게.
 */
public final class ChatAnnouncer {

    private ChatAnnouncer() {
    }

    public static void announce(World world, String message) {
        for (Player player : world.getPlayers()) {
            player.sendMessage("§7" + message);
        }
    }
}
