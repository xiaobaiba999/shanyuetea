<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CategoryVO, DrinkVO, ShopVO } from '@/types'
import { getCategories, getDrinks } from '@/api/menu'
import { getShopStatus } from '@/api/shop'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import StoreHeader from '@/components/StoreHeader.vue'
import ProductCard from '@/components/ProductCard.vue'
import SpecSheet from '@/components/SpecSheet.vue'
import CartBar from '@/components/CartBar.vue'
import ProductImage from '@/components/ProductImage.vue'

/**
 * 点单主页：门店横幅 + 招牌横滑（自动轮播）+ 分类侧栏/商品列表滚动联动 + 规格弹窗 + 购物车栏
 */
const router = useRouter()
const cart = useCartStore()

const shop = ref<ShopVO | null>(null)
const categories = ref<CategoryVO[]>([])
const drinks = ref<DrinkVO[]>([])
const loading = ref(true)

const activeCat = ref(0)
const specProduct = ref<DrinkVO | null>(null)
const specShow = ref(false)
const scrollEl = ref<HTMLElement | null>(null)
const clickLock = ref(false)

const grouped = computed(() =>
  categories.value.map((c) => ({
    ...c,
    products: drinks.value.filter((d) => d.categoryId === c.id),
  })),
)

const featured = computed(() => drinks.value.filter((d) => d.featured === 1))

const open = computed(() => shop.value?.status === 1)

/* ---------------- 商品搜索 ----------------
 * 匹配商品名 / 描述 / 所属分类名；搜索态下隐藏招牌横滑与分类导航，平铺展示结果。 */
const keyword = ref('')
const searching = computed(() => keyword.value.trim().length > 0)

const searchResults = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return []
  const catNameOf = (id: number) => categories.value.find((c) => c.id === id)?.name ?? ''
  return drinks.value.filter((d) => {
    if (d.status !== 1) return false
    return (
      d.name.toLowerCase().includes(kw) ||
      d.description.toLowerCase().includes(kw) ||
      catNameOf(d.categoryId).toLowerCase().includes(kw)
    )
  })
})

/** 进入搜索态时暂停招牌轮播（避免后台定时器空转），退出后恢复 */
watch(searching, (on) => {
  if (on) stopFeatAuto()
  else if (featured.value.length > 1) startFeatAuto()
})

/* ---------------- 招牌自动轮播 ----------------
 * 每 3.5s 平滑滚动到下一张，滚到末尾后回到开头；
 * 用户触摸/悬停时暂停（避免抢交互），离开后恢复。 */
const featuredEl = ref<HTMLElement | null>(null)
const featuredIdx = ref(0)
let featTimer: number | null = null

function scrollToCard(idx: number) {
  const el = featuredEl.value
  if (!el) return
  const cards = el.querySelectorAll<HTMLElement>('.featured-card')
  if (!cards.length) return
  const i = ((idx % cards.length) + cards.length) % cards.length
  featuredIdx.value = i
  el.scrollTo({ left: cards[i].offsetLeft - 2, behavior: 'smooth' })
}

function startFeatAuto() {
  stopFeatAuto()
  featTimer = window.setInterval(() => scrollToCard(featuredIdx.value + 1), 3500)
}

function stopFeatAuto() {
  if (featTimer != null) {
    window.clearInterval(featTimer)
    featTimer = null
  }
}

/** 用户手动滚动时同步高亮索引，轮播接着当前卡继续 */
function onFeatScroll() {
  const el = featuredEl.value
  if (!el) return
  const cards = el.querySelectorAll<HTMLElement>('.featured-card')
  const x = el.scrollLeft
  cards.forEach((c, i) => {
    if (Math.abs(c.offsetLeft - 2 - x) < 40) featuredIdx.value = i
  })
}

onMounted(async () => {
  try {
    const [s, cats, ds] = await Promise.all([getShopStatus(), getCategories(), getDrinks()])
    shop.value = s
    categories.value = cats
    drinks.value = ds
    activeCat.value = cats[0]?.id ?? 0
    await nextTick()
    if (featured.value.length > 1) startFeatAuto()
  } catch {
    /* 网络层已 toast */
  } finally {
    loading.value = false
  }
  cart.init().catch(() => {})
})

onBeforeUnmount(() => {
  stopFeatAuto()
})

function openSpec(d: DrinkVO) {
  specProduct.value = d
  specShow.value = true
}

/** 点分类 -> 右侧平滑滚到分组 */
function onCatClick(id: number) {
  activeCat.value = id
  const el = scrollEl.value?.querySelector<HTMLElement>(`[data-section="${id}"]`)
  if (el && scrollEl.value) {
    clickLock.value = true
    scrollEl.value.scrollTo({ top: el.offsetTop - 8, behavior: 'smooth' })
    window.setTimeout(() => (clickLock.value = false), 650)
  }
}

/** 右侧滚动 -> 高亮左栏（点击滚动期间锁定） */
function onScroll() {
  if (clickLock.value || !scrollEl.value) return
  const top = scrollEl.value.scrollTop
  let current = grouped.value[0]?.id ?? 0
  scrollEl.value.querySelectorAll<HTMLElement>('[data-section]').forEach((el) => {
    if (el.offsetTop - 16 <= top) current = Number(el.dataset.section)
  })
  activeCat.value = current
}
</script>

<template>
  <div class="home">
    <StoreHeader v-if="shop" :shop="shop" />
    <div v-else class="header-fallback" />

    <!-- 商品搜索框（玻璃拟态：宣纸半透明 + 毛玻璃 + 底部发丝线） -->
    <div class="search-wrap glass">
      <div class="search-box" :class="{ focus: searching }">
        <svg class="search-ico" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="M16.5 16.5L21 21" />
        </svg>
        <input
          v-model="keyword"
          class="search-input"
          type="search"
          placeholder="搜饮品 / 轻食，如「拿铁」「可颂」"
          enterkeyhint="search"
          aria-label="搜索商品"
        />
        <button v-if="searching" class="search-clear" aria-label="清空搜索" @click="keyword = ''">
          <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
    </div>

    <main class="menu-board">
      <!-- 左侧分类竖栏（搜索态下隐藏） -->
      <aside v-if="!searching" class="cat-nav no-scrollbar" aria-label="商品分类">
        <button
          v-for="c in grouped"
          :key="c.id"
          class="cat-item"
          :class="{ active: c.id === activeCat }"
          @click="onCatClick(c.id)"
        >
          <span v-if="c.id === activeCat" class="active-bar" />
          <span class="cat-name">{{ c.name }}</span>
        </button>
      </aside>

      <!-- 右侧商品列表 -->
      <div ref="scrollEl" class="product-scroll no-scrollbar" @scroll.passive="onScroll">
        <van-skeleton v-if="loading" class="sk" :row="3" title />

        <!-- 搜索结果态 -->
        <template v-else-if="searching">
          <section class="cat-section search-section">
            <h2 class="section-title">
              <span class="title-mark" aria-hidden="true" />
              搜索结果
              <span class="section-count">（{{ searchResults.length }}）</span>
            </h2>
            <div class="section-list">
              <ProductCard v-for="p in searchResults" :key="p.id" :drink="p" @spec="openSpec" />
            </div>
            <van-empty
              v-if="!searchResults.length"
              image="search"
              description="没有找到相关商品，换个关键词试试"
            />
            <p v-else class="search-hint">支持按商品名 / 描述 / 分类名搜索</p>
          </section>
        </template>

        <template v-else>
          <!-- 招牌推荐横滑（scroll-snap） -->
          <section v-if="featured.length" class="featured">
            <div class="featured-head">
              <h2>今日招牌 · 店长最爱</h2>
              <span class="featured-tip">第二杯半价</span>
            </div>
            <div
              ref="featuredEl"
              class="featured-scroll no-scrollbar"
              @scroll.passive="onFeatScroll"
              @pointerenter="stopFeatAuto"
              @pointerleave="startFeatAuto"
              @touchstart.passive="stopFeatAuto"
            >
              <button
                v-for="(p, fi) in featured"
                :key="p.id"
                class="featured-card"
                :class="{ current: fi === featuredIdx }"
                @click="openSpec(p)"
              >
                <div class="featured-img">
                  <ProductImage :src="p.image" :alt="p.name" :name="p.name" :food="p.categoryId === 5" />
                </div>
                <div class="featured-shade" />
                <div class="featured-info">
                  <p class="featured-name">{{ p.name }}</p>
                  <p class="featured-price">¥{{ fmtPrice(p.price) }}</p>
                </div>
              </button>
            </div>
            <!-- 轮播指示点 -->
            <div v-if="featured.length > 1" class="featured-dots" aria-hidden="true">
              <span v-for="(p, di) in featured" :key="p.id" class="dot" :class="{ on: di === featuredIdx }" />
            </div>
          </section>

          <!-- 分类分区 -->
          <section v-for="c in grouped" :key="c.id" :data-section="c.id" class="cat-section">
            <h2 class="section-title">
              <span class="title-mark" aria-hidden="true" />
              {{ c.name }}
              <span class="section-count">（{{ c.products.length }}）</span>
            </h2>
            <div class="section-list">
              <ProductCard v-for="p in c.products" :key="p.id" :drink="p" @spec="openSpec" />
              <p v-if="!c.products.length" class="section-empty">该分类暂无在售商品</p>
            </div>
          </section>

          <van-empty v-if="!searching && !grouped.length" description="菜单加载中，请稍候…" />
        </template>

        <div class="scroll-pad" />
      </div>
    </main>

    <CartBar :shop-open="open" @checkout="router.push('/confirm')" />

    <SpecSheet v-model:show="specShow" :product="specProduct" />

    <!-- 打烊全页遮罩 -->
    <Transition name="fade">
      <div v-if="shop && shop.status === 0" class="closed-mask">
        <div class="closed-card">
          <svg class="closed-moon" viewBox="0 0 24 24" width="42" height="42" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
          </svg>
          <h3>已打烊</h3>
          <p class="closed-hours">营业时间 {{ shop.openHours }}</p>
          <p class="closed-tip">明天再来喝一杯吧</p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  height: 100dvh;
}

/* 搜索框：背景/毛玻璃/底边线由全局 .glass 提供 */
.search-wrap {
  flex-shrink: 0;
  padding: 8px 12px 4px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--paper-deep);
  border: 1.5px solid var(--card-line);
  box-shadow: 0 1px 2px rgba(43, 38, 32, 0.04);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.search-box.focus {
  border-color: var(--gold);
  box-shadow: 0 0 0 3px rgba(200, 155, 90, 0.22);
}

.search-ico {
  flex-shrink: 0;
  color: var(--ink-3);
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: 13px;
  color: var(--ink);
  background: transparent;
  border: none;
  outline: none;
}

.search-input::placeholder {
  color: var(--ink-3);
}

.search-input::-webkit-search-cancel-button {
  display: none; /* 用自绘清空按钮，风格统一 */
}

.search-clear {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  color: var(--ink-3);
  border-radius: 50%;
  background: rgba(43, 38, 32, 0.08);
}

.search-clear:active {
  transform: scale(0.9);
}

.search-hint {
  margin-top: 4px;
  font-size: 10px;
  color: var(--ink-3);
  text-align: center;
}

.header-fallback {
  flex-shrink: 0;
  height: 132px;
  background: linear-gradient(150deg, var(--pine-deep), var(--pine) 55%, var(--pine-deep));
}

.menu-board {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 左侧分类栏：宣纸深底 + 墨绿圆点激活 */
.cat-nav {
  flex-shrink: 0;
  width: 84px;
  overflow-y: auto;
  padding: 8px 0;
  background: var(--paper-deep);
}

.cat-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  width: 100%;
  padding: 14px 8px;
  font-size: 12px;
  color: var(--ink-2);
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.cat-item.active {
  background: var(--paper);
  color: var(--ink);
  font-weight: 700;
}

/* 激活态：左侧 8px 墨绿圆点 */
.active-bar {
  position: absolute;
  left: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pine);
  box-shadow: 0 0 0 3px rgba(47, 82, 51, 0.14);
}

.cat-name {
  line-height: 1.25;
  text-align: center;
}

/* 右侧列表 */
.product-scroll {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 12px 12px 0;
}

.scroll-pad {
  height: 128px;
}

/* 招牌横滑 */
.featured {
  padding-top: 2px;
}

.featured-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 2px;
}

.featured-head h2 {
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
}

.featured-tip {
  font-size: 10px;
  color: var(--ink-3);
}

.featured-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
  scroll-snap-type: x mandatory;
}

.featured-card {
  position: relative;
  flex-shrink: 0;
  width: 150px;
  height: 112px;
  border-radius: var(--radius-card);
  overflow: hidden;
  text-align: left;
  box-shadow: 0 2px 12px rgba(43, 38, 32, 0.1);
  scroll-snap-align: start;
  background: var(--paper-deep);
}

.featured-img {
  position: absolute;
  inset: 0;
}

.featured-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 35%, rgba(0, 0, 0, 0.58));
}

.featured-info {
  position: absolute;
  left: 10px;
  right: 8px;
  bottom: 7px;
  color: #fff;
}

.featured-name {
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.featured-price {
  margin-top: 1px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-serif);
  color: #e8c886;
}

/* 招牌卡片：当前轮播卡轻微提亮放大 */
.featured-card {
  transition:
    transform 0.35s ease,
    box-shadow 0.35s ease;
}

.featured-card.current {
  transform: scale(1.03);
  box-shadow: 0 4px 16px rgba(43, 38, 32, 0.16);
}

/* 轮播指示点 */
.featured-dots {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-top: 7px;
}

.featured-dots .dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: rgba(43, 38, 32, 0.18);
  transition: all 0.3s ease;
}

.featured-dots .dot.on {
  width: 14px;
  background: var(--pine);
}

/* 分区：衬线标题 + 茶金短横 */
.cat-section {
  padding-top: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 0 2px;
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
}

.title-mark {
  display: inline-block;
  width: 14px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #dcb87e, var(--gold));
}

.section-count {
  font-size: 10px;
  font-weight: 400;
  color: var(--ink-3);
}

.section-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-empty {
  padding: 12px 2px;
  font-size: 11px;
  color: var(--ink-3);
}

.sk {
  margin-top: 8px;
}

/* 打烊遮罩 */
.closed-mask {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  z-index: 300;
  display: grid;
  place-items: center;
  background: rgba(43, 38, 32, 0.55);
  backdrop-filter: blur(6px);
}

.closed-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 78%;
  padding: 30px 20px;
  border-radius: var(--radius-card);
  background: var(--card);
  border: 1px solid var(--card-line);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.25);
}

.closed-moon {
  color: var(--gold);
}

.closed-card h3 {
  margin-top: 4px;
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 3px;
}

.closed-hours {
  font-size: 13px;
  font-weight: 600;
  color: var(--pine);
}

.closed-tip {
  font-size: 11px;
  color: var(--ink-3);
}
</style>
