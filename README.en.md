<p align="center">
  <a href="https://www.agents-anywhere.com/en"><img src="docs/images/readme-hero-en.webp" alt="Agents Anywhere: an agent workbench across your devices. Supports Codex, Claude Code and DeepSeek Harness, with more agents coming soon." width="100%"></a>
</p>

<p align="center">
  <strong>Connect your work machines. Manage AI agents from desktop, mobile and Web.</strong><br>
  Open source · Multiple agents · Sessions and workspaces · Self-hosting
</p>

<p align="center">
  <a href="https://www.agents-anywhere.com/en">Website</a> ·
  <a href="#downloads-and-access">Download</a> ·
  <a href="https://web.agents-anywhere.com">Open Web</a> ·
  <a href="docs/README.md">Documentation</a> ·
  <a href="README.md">简体中文</a>
</p>

<p align="center">
  <a href="docs/releases/2.0.3.md"><img src="https://img.shields.io/badge/release-2.0.3-222222?style=flat" alt="Release 2.0.3"></a>
  <a href="#license"><img src="https://img.shields.io/badge/license-MIT-222222?style=flat" alt="MIT"></a>
  <a href="docker/README.md"><img src="https://img.shields.io/badge/self--hosted-Docker-222222?style=flat" alt="Self-host with Docker"></a>
</p>

**Agents Anywhere** is an open-source agent workbench across your devices. Connect a work machine running **Codex, Claude Code or DeepSeek Harness**, then view sessions, respond to requests, manage files and use terminals from desktop, mobile and Web. Agents execute tasks on the connected work machine.

> ### About this repository
>
> This is a fork of [anywhere-labs/Agents-Anywhere](https://github.com/anywhere-labs/Agents-Anywhere). All upstream
> clients (mobile, desktop, Web, server, connector) are kept as they are; the addition is a **remote control client
> for round-screen smartwatches** under `android/watch/`, with the layout and input model rebuilt for a 466×466 panel.
> Both upstream and this fork are released under [MIT](LICENSE), and upstream attribution is preserved.

## Watch client (added by this fork)

The watch does not run agents. It is a remote control: the agent keeps running on the machine with the Connector,
and the watch shows the conversation, sends replies, answers approvals and switches models. The four pages are
Conversation, Workspace (grouped by working directory, filterable per device, with a "new session" entry),
Actions (takeover, model, permission, approvals, usage), and Account.

<p align="center">
  <img src="android/watch/mockups/ui-preview-v2.png" alt="Watch client layout preview" width="70%">
  <br>
  <sub>Layout preview (not a device screenshot)</sub>
</p>

**Download**: the [latest release](https://github.com/NatsuYutori/Agent_anywhere_for_watch/releases/latest) carries the watch APK and a full source archive.

Target: round 466×466 panels such as the OPPO Watch X series, Android 11 (API 30), `armeabi-v7a`; `minSdk 26`,
`targetSdk 36`. What the fork has to handle that the phone client does not: inscribed-circle geometry, the
rotation crown (one notch scrolls the current page, never changes pages), live output over the upstream
`feature/realtime` WebSocket with polling as fallback, a one-word conversation state (live / reconnecting /
run failed / blocked / device offline), usage parsed from events, per-device filtering for accounts with several
work machines, and a one-screen new-session composer.

Details, protocol checks, geometry constants, crown calibration and known limitations:
**[android/watch/README.zh-CN.md](android/watch/README.zh-CN.md)** (Chinese).

```bash
cd android
./gradlew :watch:assembleRelease      # needs android/watch/keystore.properties
./gradlew :watch:testDebugUnitTest    # geometry, crown counting, usage parsing, approval forms, auth payloads
```

### Following upstream

This repository is a real **fork**: `main` sits directly on the upstream `main`, with the watch client
as one commit on top (currently 1 ahead, 0 behind). To take upstream changes:

```bash
git fetch upstream main      # upstream already points at anywhere-labs/Agents-Anywhere
git merge upstream/main
cd android && ./gradlew :watch:testDebugUnitTest
```

Recompile the watch module after a merge that touches the shared modules it reuses
(`android/app/.../feature/auth|devices|realtime|sessiondetail|sessions` and `api/`). 2.1.0 removed
`steer` (sending while a turn runs), so the watch now always calls `sendMessage` and reports a
refusal as "the runtime is not accepting messages" — that kind of drift shows up as one compile error.

## Downloads and access

Install the desktop client on your work machine, then access it from your phone, tablet or Web. Use the Connector CLI to connect Linux and headless servers. Each client can connect to Cloud or a self-hosted service.

**Use Web directly:** open [web.agents-anywhere.com](https://web.agents-anywhere.com) and sign up or sign in to get started. Servers are hosted in mainland China, where users can expect the best connection experience.

| Platform | Get the client |
| --- | --- |
| **macOS** | [Universal DMG · 2.0.3](https://modelscope.cn/models/t4wefan/deepseek-harness-desktop/resolve/master/Agents%20Anywhere-2.0.3-universal.dmg) |
| **Windows** | [x64 installer · 2.0.3](https://modelscope.cn/models/t4wefan/deepseek-harness-desktop/resolve/master/Agents%20Anywhere%20Setup%202.0.3.exe) |
| **iOS / iPadOS** | [Download on the App Store](https://apps.apple.com/cn/app/agents-anywhere/id6787125178) · [Join TestFlight](https://testflight.apple.com/join/GKGaut99) |
| **Android** | [APK · 2.0.3](https://modelscope.cn/models/t4wefan/deepseek-harness-desktop/resolve/master/agents-anywhere-2.0.3-release.apk) |
| **Web** | [Open Web](https://web.agents-anywhere.com) |
| **Linux / headless** | [Run the Connector CLI](connector/README.md) |

See the [download page](https://www.agents-anywhere.com/en/download) for platform details. Read the [upgrade guide](docs/upgrading.md) before upgrading a legacy deployment.

<details>
<summary>Platform requirements, installers and updates</summary>

- macOS: Universal for Apple Silicon / Intel; signed and notarized; requires macOS 12 or later.
- Windows: x64 desktop workbench with a managed Connector; the current installer is not code signed.
- Android: Android 8.0 or later.
- iOS / iPadOS: download from the App Store, or install the beta through TestFlight; beta availability is shown on the invitation page.
- Linux / headless: run the Connector on the work machine and control it from another client.

The macOS, Windows and Android files are **Agents Anywhere** installers hosted in the ModelScope repository `t4wefan/deepseek-harness-desktop`; the same files are also attached to the [v2.0.3 GitHub Release](https://github.com/anywhere-labs/Agents-Anywhere/releases/tag/v2.0.3). Historical 0.1.x GitHub Releases are not the 2.0 download channel. In-app update addresses in 2.0.3 and earlier clients are placeholders; download manually using the links above. Starting with 2.1.0, **Update now** in Desktop (macOS, Windows) and Android downloads the latest installer from the official site.

`main` is the current development branch. New source fixes may not yet be included in the 2.0.3 installers. Each client and service releases PATCH versions independently, and database schema revisions are numbered separately; see the [versioning rules](docs/versioning.md). See the [2.0.3 release notes](docs/releases/2.0.3.md) for release scope.

</details>

<details open>
<summary>❤️ Sponsors</summary>

| Logo | Description |
| --- | --- |
| <a href="https://dshdesktop.cn/sponsors/wuying"><img src="docs/images/sponsors/wuying-cloud-computer-logo.png" alt="Alibaba Cloud Wuying Cloud Computer" width="96"></a> | [**Alibaba Cloud · Wuying Cloud Computer**](https://dshdesktop.cn/sponsors/wuying)<br>Thank you to **Alibaba Cloud** Wuying Cloud Computer for sponsoring this project! Wuying Cloud Computer Personal Edition provides cloud computers for individual users, hosting computing resources, storage, and desktop environments in the cloud. Access your computer from a variety of devices and choose specifications to suit your needs, whether for remote work, learning, development, or light creative tasks.<br><br>[**Open WeChat to register →**](https://dshdesktop.cn/sponsors/wuying) |
| <a href="https://88api.ai/sign-up?aff=vQMT"><img src="docs/images/sponsors/88api-logo.png" alt="88API" width="120"></a> | [**88API Token Aggregation Platform**](https://88api.ai/sign-up?aff=vQMT)<br><br>🧠 Access language and coding models including GPT, Claude, Gemini, Grok, DeepSeek, Kimi, and GLM;<br>🎨 Image models: GPT-Image, Gemini, Grok, and more;<br>🎬 Video models: Seedance, Veo, MiniMax H3, Kling, Grok, and more;<br>🎙️ Speech capabilities: Whisper, TTS, and more. From copywriting and image generation and editing to video generation and voiceovers.<br>🎁 New users receive trial credits upon registration to test model capabilities. Live customer support is available on the site!<br>👉 Operated by a registered Hong Kong company · Stable availability · Fully operational, full-capability service · No reduced model intelligence · Invoices available<br><br>[**Sign up now →**](https://88api.ai/sign-up?aff=vQMT) |

</details>

## Agents and workspaces

<p align="center">
  <img src="docs/images/readme-workbench.webp" alt="Windows workbench: devices, projects and sessions on the left; an agent's task result on the right." width="100%">
</p>

<a id="features"></a>

| What you want to do | In Agents Anywhere |
| --- | --- |
| **Manage projects and sessions** | Switch between devices, projects and sessions, and follow progress through live timelines. |
| **Approve actions and respond to requests** | Respond to approvals and input requests; interrupt or continue tasks where the runtime supports it. |
| **Browse files and use terminals** | Browse and preview files, upload and download attachments, and open remote shells and interactive terminals. |
| **Configure agents** | Configure Codex, Claude Code and DSH; choose models, permissions and actions from the capabilities each runtime supports. |

A runtime is the component that runs or connects an agent on your work machine. Model accounts and usage charges follow the rules of the agent you use. Capabilities vary by runtime; follow the options shown in the client. [Connect DSH →](dsh-bridge-next/README.md)

## Desktop, mobile and Web

<p align="center">
  <img src="docs/images/readme-mobile.webp" alt="Real iPhone, iPad and Android clients: following conversations, continuing work and answering an agent's input request." width="100%">
</p>

Check progress and reply from your phone, open the workspace on your tablet, then continue at your computer. Sign in to the same service and account to access your devices and sessions from different clients.

**Your phone controls the work; the connected machine runs it.** Keep that machine powered on and online, with its Connector and runtime running. The iPad capture shows an agent waiting for the user’s input. Screenshots retain their original interface language.

## First use

1. **Sign in to a service.** Install a client or open Web. Sign in to Cloud or enter your self-hosted service address.
2. **Connect your work machine.** Desktop includes a managed local Connector; servers and headless machines use the [Connector CLI](connector/README.md).
3. **Prepare your agent and project.** Configure the runtime, account and working directory on that machine, then open or create a session.
4. **Access from another device.** Sign in to the same service and account from your phone, tablet or another computer.

See [Getting started](docs/getting-started.md) for pairing steps, login troubleshooting and background operation.

## Execution and self-hosting

Agents use the workspace and permissions of the Connector machine. Connect to Cloud or deploy the Agents Anywhere service on your own server.

```mermaid
flowchart LR
    Clients["Desktop · Mobile · Web"] <-->|"Access and control"| Server["Agents Anywhere Server"]
    Server <-->|"Connector RPC"| Connector["Connector on your work machine"]
    Connector <--> Agents["Codex · Claude Code · DSH"]
    Connector <--> Workspace["Workspace · Files · Terminal"]
```

Session content, timelines and uploaded attachments may pass through or be stored by Server. With self-hosting, your instance handles this server-side data.

<a id="self-hosting-quickstart"></a>

### Deploy with Docker

After cloning the repository, run from its root. Replace the example password and secret first:

```bash
POSTGRES_PASSWORD=replace-with-a-strong-password \
AGENT_SERVER_SECRET=replace-with-a-long-random-secret \
docker compose -f docker/docker-compose.postgres.yml up --build
```

Open `http://127.0.0.1:5174`, retrieve the setup token from Server logs and create the first administrator. Compose includes PostgreSQL, Redis, a migration job and the Server hosting the static Web client.

[Deployment guide](docker/README.md) · [Backups and upgrades](docs/upgrading.md) · [Server documentation](server/README.md)

<a id="architecture-and-source"></a>

## For developers

Development uses **Python 3.12+ / uv / Node.js 22 / Corepack + Yarn**. Start with the [development guide](docs/development.md) for source setup and headless checks. Most detailed guides currently use Chinese.

| What to explore | Start here |
| --- | --- |
| Architecture and API | [Server architecture](docs/server-architecture.md) · [API documentation](docs/api/README.md) |
| Agent integrations and local execution | [Connector](connector/README.md) · [Runtime protocol](docs/runtime-protocol/README.md) |
| Web and desktop clients | [Web source](web-next/) · [Desktop Workbench](desktop-workbench/README.md) |
| Native mobile clients | [Android](android/README.md) · [iOS source](ios/) |
| DSH integration | [DSH Bridge Next](dsh-bridge-next/README.md) |
| More documentation | [Documentation index](docs/README.md) · [Protocol contracts](contracts/) |

Report problems in [Issues](https://github.com/anywhere-labs/Agents-Anywhere/issues) or contribute through [Pull Requests](https://github.com/anywhere-labs/Agents-Anywhere/pulls). Include the client version, operating system, runtime type and reproduction steps, with credentials removed from logs.

<a id="cloud-access-and-community"></a>

## Community and feedback

Join the community to share your experience, report problems or contribute. Scan a code to join WeChat or QQ. Self-hosted accounts are managed by your own administrator.

| WeChat | QQ |
| --- | --- |
| <img src="docs/contact/wechat-beta.png" alt="WeChat community QR code" width="160"> | <img src="docs/contact/qq-beta.png" alt="QQ community QR code" width="160"> |

## License

MIT. See [image sources and reproduction](docs/readme-artwork/README.md) for product screenshot and brand-asset notes.
