package dev.hometetris.core

/** 아이템을 누가 맞는지. */
enum class ItemTarget { SELF, ENEMY }

/**
 * 아이템 모드에서 쓰는 아이템들. 넷마블 테트리스에서 익숙한 것들로 골랐다.
 *
 * 규칙은 단순하게 뒀다 - **줄을 지우면 하나 얻고, 슬롯은 하나뿐이다.**
 * 이미 들고 있으면 더 안 들어온다. 그래서 "언제 쓸지"가 판단거리가 된다.
 */
enum class ItemKind(
    val label: String,
    /** 버튼에 들어갈 짧은 기호. 이모지는 기기마다 다르게 나와서 안 쓴다. */
    val icon: String,
    val target: ItemTarget,
    val desc: String,
) {
    CLEAN("청소", "=", ItemTarget.SELF, "내 판 아래 두 줄을 지웁니다"),
    BOMB("폭탄", "*", ItemTarget.SELF, "내 판 아래쪽 블록을 듬성듬성 날립니다"),
    QUAKE("지진", "~", ItemTarget.ENEMY, "상대 판이 좌우로 어긋납니다"),
    FOG("안개", "?", ItemTarget.ENEMY, "상대 화면을 잠시 가립니다"),
    NO_ROTATE("회전금지", "X", ItemTarget.ENEMY, "상대가 잠시 회전하지 못합니다"),
    RUSH("가속", ">", ItemTarget.ENEMY, "상대 블록이 잠시 빨리 떨어집니다");

    companion object {
        /** 방해용만. 공격 아이템을 고를 때 쓴다. */
        val enemyItems = entries.filter { it.target == ItemTarget.ENEMY }

        fun byName(name: String): ItemKind? = entries.firstOrNull { it.name == name }
    }
}

/** 상대에게 걸린 효과가 얼마나 가는지. 한곳에 모아 둬야 균형을 잡기 쉽다. */
object ItemDuration {
    const val FOG_MS = 6_000L
    const val NO_ROTATE_MS = 5_000L
    const val RUSH_MS = 8_000L

    /** 가속이 걸린 동안 낙하가 몇 배 빨라지는지. */
    const val RUSH_FACTOR = 3
}
