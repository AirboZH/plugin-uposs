<H2 align="center">又拍云OSS <a href="https://github.com/halo-dev/halo#">Halo</a>插件</H2>

<p align="center">
<a href="https://github.com/AirboZH/plugin-uposs/releases"><img alt="GitHub release" src="https://img.shields.io/github/release/AirboZH/plugin-uposs.svg?style=flat-square&include_prereleases" /></a>
<a href="https://github.com/AirboZH/plugin-uposs/commits"><img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/AirboZH/plugin-uposs.svg?style=flat-square" /></a>
<br />
<a href="https://github.com/AirboZH/plugin-uposs/issues">Issues</a>
<a href="mailto:airbozh@gmail.com">邮箱</a>
</p>

------------------------------

## **为 Halo 2.0 提供又拍云 OSS 的存储策略**

### 使用方法

[^_^]: 目前设置了 GitHub Action 的 Push 构建，你可以在 ${url}/actions 的每个构建详情中下载最新构建的 JAR 文件。然后在 Halo 后台的插件管理上传即可。

- 上传并启用插件。
- 进入后台附件管理。
- 点击右上角的存储策略，在存储策略弹框的右上角可新建又拍云 OSS 存储策略。
- 创建完成之后即可在上传的时候选择新创建的又拍云 OSS 存储策略。

### 开发环境

```
git clone git@github.com:AirboZH/plugin-uposs.git
./gradlew build
```
**修改 Halo 的配置文件**
```

plugin:
  runtime-mode: development # development, deployment
  classes-directories:
    - "build/classes"
    - "build/resources"
  lib-directories:
    - "libs"
  fixedPluginPath:
    - "path/to/plugin-uposs"
```

启动 Halo 之后即可在后台插件管理看到此插件。

### 生产构建

```
./gradlew build
```

构建完成之后，可以在 build/libs 目录得到插件的 JAR 包，在 Halo 后台的插件管理上传即可。
