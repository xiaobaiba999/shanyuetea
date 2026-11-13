<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DEFAULT_DRINK_EMOJI, DEFAULT_FOOD_EMOJI, emojiForName } from '@/utils/emoji'

/**
 * 商品图：加载失败时降级为「咖啡色渐变底 + emoji 兜底」
 * emoji 优先级：显式传入 > name 精确映射表 > 分类兜底（饮品 🧋 / 轻食甜点 🍰）
 */
const props = withDefaults(
  defineProps<{
    src: string
    alt: string
    /** 商品名，用于查 emoji 映射表 */
    name?: string
    /** 是否轻食甜点分类（决定默认兜底 emoji） */
    food?: boolean
    /** 显式指定兜底 emoji */
    emoji?: string
  }>(),
  { name: '', food: false, emoji: '' },
)

const error = ref(false)
watch(
  () => props.src,
  () => (error.value = false),
)

const fallbackEmoji = computed(
  () => props.emoji || emojiForName(props.name) || (props.food ? DEFAULT_FOOD_EMOJI : DEFAULT_DRINK_EMOJI),
)
</script>

<template>
  <img v-if="!error && src" class="product-img" :src="src" :alt="alt" loading="lazy" @error="error = true" />
  <div v-else class="product-img img-fallback" role="img" :aria-label="alt">
    <span>{{ fallbackEmoji }}</span>
  </div>
</template>

<style scoped>
.product-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.img-fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #f3dfc2, #f7ecd9 45%, #ecd3ae);
}

.img-fallback span {
  font-size: 26px;
  opacity: 0.85;
  filter: saturate(0.9);
}
</style>
