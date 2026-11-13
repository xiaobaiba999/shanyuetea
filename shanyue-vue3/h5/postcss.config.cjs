/**
 * 空 PostCSS 配置：本工程不使用 Sass/Less/PostCSS 插件。
 * 作用是阻止 Vite 向上查找到 /home/z/my-project/postcss.config.mjs（Next.js 原型的 tailwind 配置）导致构建失败。
 */
module.exports = { plugins: {} }
