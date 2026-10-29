# 无遮远程 ClearRDP 隐私政策

生效日期：2026 年 10 月 30 日

无遮远程（ClearRDP，包名 `io.github.icangotoanywhere.clearrdp`）是一个开源的远程桌面客户端，由个人开发者 icangotoanywhere 开发并维护。本政策说明这个应用如何处理你的信息。

## 一句话概括

**本应用不收集、不上传、不出售任何个人信息。**它没有账号系统，不含广告，不含统计或分析组件，也不会把任何数据发给开发者或其他第三方。

## 保存在你手机上的信息

为了让你下次一点就能连上，应用会把你添加的电脑信息保存在手机上：

- 名称、地址和端口
- Windows 用户名、密码和域（密码可以不填，留空时每次连接再输入）
- 画质、声音、操作模式等设置
- 手机输入法的高度（用于对齐屏幕布局）

这些信息只保存在本应用的私有存储空间里，其他应用无法读取，也不会被备份到云端（应用已关闭系统自动备份）。在应用里删除某台电脑，或卸载应用，这些信息就会一起删除。

## 连接时发送的信息

只有在你点击连接某台电脑时，应用才会联网，而且只连接**你自己填写的那台电脑**。连接时会发送：

- 你填写的用户名、密码和域，用于登录那台电脑（通过 RDP 协议加密传输）
- 手机型号，作为客户端名称，显示在那台电脑的远程会话信息里
- 你的触摸、鼠标和键盘操作
- 剪贴板内容：手机和电脑之间会同步复制的文字，方便双向粘贴

应用也会接收那台电脑的画面、声音和剪贴板文字，只在本机显示和播放，不会保存，也不会转发。

## 应用申请的权限

| 权限 | 用途 |
|---|---|
| 网络访问（INTERNET） | 连接你指定的电脑 |
| 查看网络状态（ACCESS_NETWORK_STATE） | 判断当前是否联网 |
| 修改音频设置（MODIFY_AUDIO_SETTINGS） | 在手机上播放远程电脑的声音 |

应用不申请位置、通讯录、相机、麦克风、存储、电话等权限。

## 第三方组件

远程桌面协议由开源项目 [FreeRDP](https://www.freerdp.com/) 实现，它作为应用的一部分在你的手机上运行，同样不会向任何第三方发送数据。

## 儿童

本应用面向需要远程操作电脑的成年用户，不针对儿童，也不会有意收集儿童的信息。

## 政策更新

如果本政策有变化，会更新本页面并修改上面的生效日期。本页面的历史版本可以在 [GitHub 提交记录](https://github.com/icangotoanywhere/ClearRDP/commits/main/PRIVACY.md) 中查到。

## 联系方式

有任何问题，请在 GitHub 上[提交 Issue](https://github.com/icangotoanywhere/ClearRDP/issues)。

---

# ClearRDP Privacy Policy

Effective date: 30 October 2026

ClearRDP (package `io.github.icangotoanywhere.clearrdp`) is an open-source remote desktop client developed and maintained by an individual developer, icangotoanywhere.

**ClearRDP does not collect, upload or sell any personal information.** It has no accounts, no ads, no analytics, and sends nothing to the developer or any other third party.

**Stored on your phone.** The computers you add are saved in the app's private storage so you can reconnect with one tap: name, address and port, Windows user name, password and domain (the password is optional), your display, sound and input settings, and the measured keyboard height used for the screen layout. Other apps cannot read this data, and it is excluded from system backup. It is deleted when you delete that computer in the app or uninstall the app.

**Sent when you connect.** The app connects only to the computer you entered, and only when you tap it. It sends that computer your credentials over the encrypted RDP protocol, your phone model as the client name, your touch, mouse and keyboard input, and clipboard text so you can copy and paste in both directions. Screen images, sound and clipboard text received from that computer are shown or played on your phone only, never stored or forwarded.

**Permissions.** INTERNET connects to your computer. ACCESS_NETWORK_STATE checks connectivity. MODIFY_AUDIO_SETTINGS plays the remote computer's sound. The app requests no location, contacts, camera, microphone, storage or phone permissions.

**Third-party components.** The RDP protocol is implemented by the open-source [FreeRDP](https://www.freerdp.com/) project, which runs inside the app on your phone and sends no data to third parties.

**Children.** The app is intended for adults who operate their own computers remotely and is not directed at children.

**Changes.** Updates will be posted on this page with a new effective date. The page's history is on [GitHub](https://github.com/icangotoanywhere/ClearRDP/commits/main/PRIVACY.md).

**Contact.** Please [open an issue](https://github.com/icangotoanywhere/ClearRDP/issues) on GitHub.
