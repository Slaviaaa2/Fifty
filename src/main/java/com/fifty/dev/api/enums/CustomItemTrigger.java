package com.fifty.dev.api.enums;

/** Lifecycle moments that can complete a custom-item advancement. */
public enum CustomItemTrigger {
    /** プレイヤーのインベントリスロットにカスタムアイテムが入ったとき。 */
    ACQUIRE,

    /** クラフト結果としてカスタムアイテムを作成したとき。 */
    CRAFT,

    /** ワールドに落ちているカスタムアイテムを拾ったとき。 */
    PICKUP,

    /** 手に持ったカスタムアイテムでクリック操作を行ったとき。 */
    INTERACT,

    /** 食料やポーションなど、カスタムアイテムを消費したとき。 */
    CONSUME,

    /** インベントリからカスタムアイテムをドロップしたとき。 */
    DROP,

    /** 耐久値を持つカスタムアイテムにダメージが適用されたとき。 */
    DAMAGE,

    /** 耐久値が尽きてカスタムアイテムが壊れたとき。 */
    BREAK,

    /** ホットバーでカスタムアイテムが入ったスロットを選択したとき。 */
    SELECT,

    /** カスタムアイテムを含むメインハンドとオフハンドを入れ替えたとき。 */
    SWAP_HANDS,

    /** カスタムアイテムを使用してブロックを設置したとき。 */
    PLACE,

    /** 設置済みのカスタムブロックをクリックしたとき。 */
    BLOCK_INTERACT,

    /** プレイヤーが設置済みのカスタムブロックを破壊したとき。 */
    BLOCK_BREAK
}
