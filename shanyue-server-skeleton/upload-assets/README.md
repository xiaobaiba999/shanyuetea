# 预置菜品图片（10 张）

`sql/data.sql` 里预置的 8 个饮品 + 2 个套餐引用了这些图片文件
（数据库存的是相对路径，如 `/upload/latte.png`）。

## 使用方法（一次性，30 秒）

把本目录下的 **全部 10 张 png** 复制到后端本地存储目录
（`application-dev.yml` 里 `shanyue.local-storage.path` 配置的路径）：

- Windows 默认：`D:/shanyue/upload`
- macOS/Linux：改成你自己的目录，如 `/Users/你的用户名/shanyue/upload`

复制完成后无需重启后端（`/upload/**` 是静态映射，文件放进目录即可访问），
小程序里下拉刷新菜单就能看到图片。

## 为什么图片要这样放？

- 数据库只存**相对路径** `/upload/xxx.png`，不存完整 URL——换服务器/换域名不用改库；
- 后端 `WebMvcConfiguration` 把 `/upload/**` 映射到本地磁盘目录，浏览器/小程序
  直接按 URL 访问（如 `http://localhost:8080/upload/latte.png`）；
- 小程序端把相对路径拼上 `BASE_URL` 再加载（`pages/menu/menu.js` 的 `imgUrl`）；
- 图片没放进去时页面显示绿色首字占位（加载失败自动兜底），不影响功能。

## 换成自己的图？

管理端「新增饮品」上传图片即可（`CommonController.upload` 会把文件写进同一目录），
文件名不必遵循本目录的命名。
