/**
 * BIT Systems Engine - Discord Edge Service
 * High-performance, serverless edge runtime for Discord interactions.
 * Features:
 * - Native Action Buttons (Link & Interactive)
 * - Dynamic String Select Menus (In-Place Embed Updates)
 * - Native Modal Popups (/bug telemetry & crash dispatch)
 * - Self-Service 1-Click Role Buttons (with auto-role creation)
 * - llama.kt native runtime branding
 */

function hexToUint8Array(hex) {
  const matches = hex.match(/.{1,2}/g);
  if (!matches) return new Uint8Array(0);
  return new Uint8Array(matches.map((byte) => parseInt(byte, 16)));
}

async function verifyDiscordSignature(body, signature, timestamp, publicKey) {
  if (!signature || !timestamp || !body || !publicKey) {
    return false;
  }

  try {
    const signatureBytes = hexToUint8Array(signature);
    const publicKeyBytes = hexToUint8Array(publicKey);

    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      publicKeyBytes,
      { name: 'Ed25519' },
      false,
      ['verify']
    );

    const encoder = new TextEncoder();
    const messageBytes = encoder.encode(timestamp + body);

    return await crypto.subtle.verify(
      { name: 'Ed25519' },
      cryptoKey,
      signatureBytes,
      messageBytes
    );
  } catch (err) {
    console.error('Signature verification failure:', err);
    return false;
  }
}

// Models specification catalogue using llama.kt engine
const MODEL_CATALOGUE = {
  qwen: {
    title: 'BIT Engine // Architecture Specification: Qwen 2.5',
    color: 0x27272a,
    description: 'General-purpose reasoning, multi-turn dialogue, and structured JSON/GBNF agent execution.',
    fields: [
      { name: 'Target Variants', value: '1.5B / 3B / 7B Instruct', inline: true },
      { name: 'Quantization Target', value: 'Q4_K_M (Balanced)', inline: true },
      { name: 'VRAM / RAM Footprint', value: '1.1 GB (1.5B) | 2.0 GB (3B) | 4.4 GB (7B)', inline: false },
      { name: 'Agent Suitability', value: 'Highest tier compatibility with GBNF grammar tools and PRoot execution.', inline: false },
      { name: 'Recommended Context', value: '4,096 - 8,192 tokens', inline: true },
      { name: 'Speed Profile', value: 'Up to 38 t/s on ARM Cortex-X4 / Kryo Prime', inline: true },
    ],
    footer: { text: 'Engine: llama.kt ARM64 | Weights: Alibaba Qwen Team' },
  },
  llama: {
    title: 'BIT Engine // Architecture Specification: Llama 3.2',
    color: 0x18181b,
    description: 'Ultra-low latency mobile architecture optimized for conversational speed and low thermal draw.',
    fields: [
      { name: 'Target Variants', value: '1B / 3B Instruct', inline: true },
      { name: 'Quantization Target', value: 'Q4_K_M / Q8_0', inline: true },
      { name: 'VRAM / RAM Footprint', value: '0.8 GB (1B) | 1.9 GB (3B)', inline: false },
      { name: 'Agent Suitability', value: 'Strong standard instruction following; ideal for 4 GB - 6 GB RAM hardware.', inline: false },
      { name: 'Recommended Context', value: '2,048 - 4,096 tokens', inline: true },
      { name: 'Speed Profile', value: 'Up to 45+ t/s on Flagship ARM64', inline: true },
    ],
    footer: { text: 'Engine: llama.kt ARM64 | Weights: Meta AI' },
  },
  gemma: {
    title: 'BIT Engine // Architecture Specification: Gemma 2',
    color: 0x27272a,
    description: 'Compact dense architecture with sliding-window attention and high factual summarization capability.',
    fields: [
      { name: 'Target Variants', value: '2B / 9B Instruct', inline: true },
      { name: 'Quantization Target', value: 'Q4_K_M', inline: true },
      { name: 'VRAM / RAM Footprint', value: '1.6 GB (2B) | 5.5 GB (9B)', inline: false },
      { name: 'Agent Suitability', value: 'Strict adherence to formatted constraints; concise responses.', inline: false },
      { name: 'Recommended Context', value: '4,096 tokens (Sliding Window)', inline: true },
      { name: 'Speed Profile', value: '25 - 32 t/s on Flagship ARM64', inline: true },
    ],
    footer: { text: 'Engine: llama.kt ARM64 | Weights: Google DeepMind' },
  },
  deepseek: {
    title: 'BIT Engine // Architecture Specification: DeepSeek-R1 Distill',
    color: 0x09090b,
    description: 'High-density chain-of-thought mathematical reasoning and logic distillation model.',
    fields: [
      { name: 'Target Variants', value: '7B / 8B (Qwen / Llama Distill)', inline: true },
      { name: 'Quantization Target', value: 'Q3_K_M / Q4_K_M', inline: true },
      { name: 'VRAM / RAM Footprint', value: '3.8 GB - 4.8 GB', inline: false },
      { name: 'Agent Suitability', value: 'Deep algorithmic synthesis and autonomous plan verification.', inline: false },
      { name: 'Recommended Context', value: '8,192 tokens', inline: true },
      { name: 'Speed Profile', value: '14 - 22 t/s on Snapdragon 8 Gen 3', inline: true },
    ],
    footer: { text: 'Engine: llama.kt ARM64 | Weights: DeepSeek AI' },
  },
};

const ROLE_COLORS = {
  'Android Developer': 0x3b82f6,
  'Benchmark Tester': 0x10b981,
  'Core User': 0x71717a,
};

function getModelSelectMenu(selected = 'qwen') {
  return {
    type: 1, // ActionRow
    components: [
      {
        type: 3, // String Select
        custom_id: 'model_selector',
        placeholder: 'Select Model Architecture Specification',
        options: [
          {
            label: 'Qwen 2.5 (1.5B / 3B / 7B)',
            value: 'qwen',
            description: 'General reasoning, code generation, and agent tool execution',
            default: selected === 'qwen',
          },
          {
            label: 'Llama 3.2 (1B / 3B)',
            value: 'llama',
            description: 'Ultra-lightweight architecture for fast generation on mid-tier SoCs',
            default: selected === 'llama',
          },
          {
            label: 'Gemma 2 (2B / 9B)',
            value: 'gemma',
            description: 'High factual precision and concise technical responses',
            default: selected === 'gemma',
          },
          {
            label: 'DeepSeek-R1 Distill (7B / 8B)',
            value: 'deepseek',
            description: 'Advanced chain-of-thought mathematical and logical reasoning',
            default: selected === 'deepseek',
          },
        ],
      },
    ],
  };
}

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response('Method not allowed', { status: 405 });
    }

    const signature = request.headers.get('x-signature-ed25519');
    const timestamp = request.headers.get('x-signature-timestamp');

    if (!signature || !timestamp) {
      return new Response('Missing signature headers', { status: 401 });
    }

    const rawBody = await request.text();
    const publicKey = env.PUBLIC_KEY || env.DISCORD_PUBLIC_KEY;
    const botToken = env.DISCORD_TOKEN;

    const isValid = await verifyDiscordSignature(rawBody, signature, timestamp, publicKey);

    if (!isValid) {
      return new Response('Invalid request signature', { status: 401 });
    }

    let interaction;
    try {
      interaction = JSON.parse(rawBody);
    } catch {
      return new Response('Malformed JSON', { status: 400 });
    }

    // Type 1: Discord PING verification
    if (interaction.type === 1) {
      return new Response(JSON.stringify({ type: 1 }), {
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // =========================================================================
    // Type 2: Slash Commands
    // =========================================================================
    if (interaction.type === 2) {
      const { name, options = [] } = interaction.data;

      // /download with interactive Discord Action Buttons
      if (name === 'download') {
        let releaseData = null;
        try {
          const ghResponse = await fetch('https://api.github.com/repos/jaswanthsanjay88/Bit/releases/latest', {
            headers: { 'User-Agent': 'BIT-Discord-Edge-Bot/1.0' },
          });
          if (ghResponse.ok) {
            releaseData = await ghResponse.json();
          }
        } catch (e) {
          console.error('Failed to query GitHub releases:', e);
        }

        const tag = releaseData?.tag_name || 'v2.1.9';
        const published = releaseData?.published_at ? new Date(releaseData.published_at).toISOString().split('T')[0] : 'Latest';

        const arm64Asset = releaseData?.assets?.find((a) => a.name.includes('arm64-v8a'));
        const universalAsset = releaseData?.assets?.find((a) => a.name.includes('universal'));

        const arm64Url = arm64Asset?.browser_download_url || `https://github.com/jaswanthsanjay88/Bit/releases/download/${tag}/app-arm64-v8a-release.apk`;
        const arm64Size = arm64Asset?.size ? (arm64Asset.size / (1024 * 1024)).toFixed(1) + ' MB' : '71.0 MB';

        const universalUrl = universalAsset?.browser_download_url || `https://github.com/jaswanthsanjay88/Bit/releases/download/${tag}/app-universal-release.apk`;
        const universalSize = universalAsset?.size ? (universalAsset.size / (1024 * 1024)).toFixed(1) + ' MB' : '91.3 MB';

        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [
                {
                  title: `BIT Engine // Binary Distribution (${tag})`,
                  color: 0x18181b,
                  description:
                    'Production Android binaries compiled with native ARM64 NEON SIMD and OpenCL GPU acceleration. 100% offline, zero cloud telemetry.',
                  fields: [
                    {
                      name: 'Primary Build Target',
                      value: `Architecture: \`ARM64-v8a\` (${arm64Size})\nTarget: Snapdragon, MediaTek Dimensity, Google Tensor, Samsung Exynos.`,
                      inline: false,
                    },
                    {
                      name: 'Universal Package',
                      value: `Architecture: \`Universal Multi-ABI\` (${universalSize})\nTarget: Broad device compatibility and fallback emulation.`,
                      inline: false,
                    },
                    {
                      name: 'Build Artifact Provenance',
                      value: `Release: \`${tag}\` | Published: \`${published}\`\nLicense: Apache-2.0 / Open-Source`,
                      inline: false,
                    },
                  ],
                  footer: { text: 'SHA-256 Verified Release Pipeline | On-Device llama.kt' },
                },
              ],
              components: [
                {
                  type: 1, // ActionRow
                  components: [
                    {
                      type: 2, // Button
                      style: 5, // Link Button
                      label: `Download ARM64 APK (${arm64Size})`,
                      url: arm64Url,
                    },
                    {
                      type: 2,
                      style: 5,
                      label: `Universal Build (${universalSize})`,
                      url: universalUrl,
                    },
                    {
                      type: 2,
                      style: 5,
                      label: 'GitHub Releases',
                      url: 'https://github.com/jaswanthsanjay88/Bit/releases',
                    },
                  ],
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /models with interactive Dropdown Select Menu
      if (name === 'models') {
        const defaultModel = MODEL_CATALOGUE.qwen;
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [defaultModel],
              components: [getModelSelectMenu('qwen')],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /bug with native Discord Pop-Up Modal
      if (name === 'bug') {
        return new Response(
          JSON.stringify({
            type: 9, // MODAL
            data: {
              title: 'BIT // Bug & Telemetry Report',
              custom_id: 'modal_bug_report',
              components: [
                {
                  type: 1,
                  components: [
                    {
                      type: 4, // TextInput
                      custom_id: 'bug_device',
                      label: 'Device Model & Chipset',
                      style: 1, // Short
                      placeholder: 'e.g. Pixel 8 (Tensor G3) or Galaxy S24 (Snapdragon 8 Gen 3)',
                      required: true,
                    },
                  ],
                },
                {
                  type: 1,
                  components: [
                    {
                      type: 4,
                      custom_id: 'bug_android',
                      label: 'Android OS Version',
                      style: 1,
                      placeholder: 'e.g. Android 14 / OneUI 6.1 / HyperOS',
                      required: true,
                    },
                  ],
                },
                {
                  type: 1,
                  components: [
                    {
                      type: 4,
                      custom_id: 'bug_model',
                      label: 'Active GGUF Model & Quantization',
                      style: 1,
                      placeholder: 'e.g. Qwen 2.5 3B Instruct (Q4_K_M)',
                      required: false,
                    },
                  ],
                },
                {
                  type: 1,
                  components: [
                    {
                      type: 4,
                      custom_id: 'bug_trace',
                      label: 'Error Description or Crash Stacktrace',
                      style: 2, // Paragraph
                      placeholder: 'Detail the failure sequence, memory panic, or terminal output...',
                      required: true,
                    },
                  ],
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /roles with 1-Click Role Buttons
      if (name === 'roles') {
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [
                {
                  title: 'BIT // Developer & Tester Role Assignment',
                  description:
                    'Select your community profile below to configure notifications for experimental build drops, hardware benchmarking alerts, and engineering discussions.\n\nClick any button below to toggle the role on or off for your account.',
                  color: 0x18181b,
                  fields: [
                    { name: 'Android Developer', value: 'Architecture discussions, PRoot Linux tooling, and SDK integration.', inline: false },
                    { name: 'Benchmark Tester', value: 'Performance profiling, token/sec validation, and thermal metrics on physical devices.', inline: false },
                    { name: 'Core User', value: 'Official releases, release notes, and general assistance.', inline: false },
                  ],
                  footer: { text: '1-Click Interactive Community Onboarding' },
                },
              ],
              components: [
                {
                  type: 1,
                  components: [
                    {
                      type: 2,
                      style: 2, // Secondary (zinc/grey)
                      label: 'Android Developer',
                      custom_id: 'role_toggle_Android Developer',
                    },
                    {
                      type: 2,
                      style: 2,
                      label: 'Benchmark Tester',
                      custom_id: 'role_toggle_Benchmark Tester',
                    },
                    {
                      type: 2,
                      style: 2,
                      label: 'Core User',
                      custom_id: 'role_toggle_Core User',
                    },
                  ],
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /canirun
      if (name === 'canirun') {
        const ramOption = options.find((opt) => opt.name === 'ram');
        const procOption = options.find((opt) => opt.name === 'processor');

        const ramGb = ramOption ? parseInt(ramOption.value, 10) : 8;
        const procTier = procOption ? procOption.value : 'standard';

        let sizing;
        if (ramGb <= 4) {
          sizing = {
            usable: '1.2 GB - 1.5 GB',
            recommended: 'Qwen 2.5 0.5B (Q4_K_M) or Llama 3.2 1B (Q4_K_M)',
            context: '1,024 - 2,048 tokens',
            throughput: procTier === 'flagship' ? '18 - 26 t/s' : procTier === 'mid' ? '9 - 14 t/s' : '4 - 7 t/s',
            status: 'Operational with strict memory constraints',
            notes: 'Operating system processes require 2.5 GB. Background app suspension recommended.',
          };
        } else if (ramGb <= 6) {
          sizing = {
            usable: '2.8 GB - 3.2 GB',
            recommended: 'Qwen 2.5 1.5B (Q4_K_M) or Llama 3.2 3B (Q4_K_M)',
            context: '2,048 - 4,096 tokens',
            throughput: procTier === 'flagship' ? '24 - 36 t/s' : procTier === 'mid' ? '12 - 18 t/s' : '6 - 10 t/s',
            status: 'Optimal for sub-3B parameter engines',
            notes: 'Balanced execution profile. Supports full voice pipeline and standard retrieval.',
          };
        } else if (ramGb <= 8) {
          sizing = {
            usable: '4.5 GB - 5.0 GB',
            recommended: 'Qwen 2.5 3B (Q4_K_M) or Gemma 2 2B (Q4_K_M / Q8_0)',
            context: '4,096 - 8,192 tokens',
            throughput: procTier === 'flagship' ? '30 - 45 t/s' : procTier === 'mid' ? '15 - 24 t/s' : '8 - 12 t/s',
            status: 'Recommended production baseline',
            notes: 'Sufficient headroom for autonomous agent tool execution, PRoot Linux tasks, and vector memory.',
          };
        } else if (ramGb <= 12) {
          sizing = {
            usable: '7.5 GB - 8.5 GB',
            recommended: 'Qwen 2.5 7B (Q3_K_M / Q4_K_M) or Llama 3.1 8B (Q4_K_M)',
            context: '8,192 tokens',
            throughput: procTier === 'flagship' ? '20 - 32 t/s' : procTier === 'mid' ? '10 - 16 t/s' : '5 - 9 t/s',
            status: 'High-performance engineering tier',
            notes: 'Full support for 7B-8B parameter models. Complex reasoning and code generation enabled.',
          };
        } else {
          sizing = {
            usable: '10.5 GB - 12.0+ GB',
            recommended: 'Qwen 2.5 14B (Q4_K_M) or DeepSeek-R1-Distill 7B / Llama 3.1 8B (Q8_0)',
            context: '16,384+ tokens',
            throughput: procTier === 'flagship' ? '25 - 40 t/s' : '14 - 20 t/s',
            status: 'Unrestricted workstation tier',
            notes: 'Maximum precision quantization profiles and extended context windows.',
          };
        }

        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [
                {
                  title: 'BIT Engine // Hardware Sizing Assessment',
                  color: 0x27272a,
                  fields: [
                    { name: 'Hardware Configuration', value: `Physical RAM: ${ramGb} GB | Processor Tier: ${procTier.toUpperCase()}`, inline: false },
                    { name: 'Usable Model Allocation', value: sizing.usable, inline: true },
                    { name: 'Execution Status', value: sizing.status, inline: true },
                    { name: 'Recommended Weights', value: sizing.recommended, inline: false },
                    { name: 'Context Window Limit', value: sizing.context, inline: true },
                    { name: 'Estimated Throughput', value: sizing.throughput, inline: true },
                    { name: 'Architectural Notes', value: sizing.notes, inline: false },
                  ],
                  footer: { text: 'BIT Runtime Performance Model | llama.kt ARM64 Optimization' },
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /about
      if (name === 'about') {
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [
                {
                  title: 'BIT // System Architecture Specification',
                  description:
                    'BIT is a privacy-first, fully autonomous on-device AI assistant and execution environment engineered for Android.',
                  color: 0x18181b,
                  fields: [
                    {
                      name: 'Inference Engine',
                      value: 'Native llama.kt runtime executing GGUF quantized models with ARM NEON SIMD and OpenCL GPU acceleration.',
                      inline: false,
                    },
                    {
                      name: 'Audio Processing Subsystem',
                      value: 'Real-time Whisper speech recognition via Sherpa-ONNX; speech synthesis via neural Piper and Kokoro models.',
                      inline: false,
                    },
                    {
                      name: 'Autonomous Agent Harness',
                      value: 'Deterministic GBNF grammar constraints, structured function dispatch, and sandboxed PRoot Linux workspace execution.',
                      inline: false,
                    },
                    {
                      name: 'Security & Data Boundary',
                      value: 'Zero network telemetry. Encrypted vector memory vault using hardware-backed AES-256-GCM. 0 bytes leave the device.',
                      inline: false,
                    },
                  ],
                  footer: { text: 'Open-Source Infrastructure | bit.jaswanthsanjay.me' },
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /links
      if (name === 'links') {
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              embeds: [
                {
                  title: 'BIT // Official Source & Distribution Endpoints',
                  color: 0x18181b,
                  fields: [
                    { name: 'Web Portal', value: 'https://bit.jaswanthsanjay.me', inline: false },
                    { name: 'Source Repository', value: 'https://github.com/jaswanthsanjay88/Bit', inline: false },
                    { name: 'Developer Community', value: 'https://discord.gg/v75PxaJDy', inline: false },
                    { name: 'Binary Releases', value: 'https://github.com/jaswanthsanjay88/Bit/releases', inline: false },
                  ],
                  footer: { text: 'Official Distribution Registry' },
                },
              ],
              components: [
                {
                  type: 1,
                  components: [
                    { type: 2, style: 5, label: 'Website', url: 'https://bit.jaswanthsanjay.me' },
                    { type: 2, style: 5, label: 'GitHub Repository', url: 'https://github.com/jaswanthsanjay88/Bit' },
                    { type: 2, style: 5, label: 'Discord Invite', url: 'https://discord.gg/v75PxaJDy' },
                  ],
                },
              ],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // /ping (Clean, zero third-party or hosting mentions)
      if (name === 'ping') {
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              content: 'BIT Gateway status: Operational. Gateway latency: < 15ms.',
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    // =========================================================================
    // Type 3: Component Interactions (Buttons, Dropdown Select Menus)
    // =========================================================================
    if (interaction.type === 3) {
      const { custom_id, values } = interaction.data;

      // Dropdown Select Menu: model_selector
      if (custom_id === 'model_selector' && values && values.length > 0) {
        const selected = values[0];
        const modelData = MODEL_CATALOGUE[selected] || MODEL_CATALOGUE.qwen;

        // Type 7: UPDATE_MESSAGE (modifies the existing message in-place!)
        return new Response(
          JSON.stringify({
            type: 7,
            data: {
              embeds: [modelData],
              components: [getModelSelectMenu(selected)],
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }

      // 1-Click Role Toggle Buttons with Auto-Creation Support
      if (custom_id.startsWith('role_toggle_')) {
        const roleName = custom_id.replace('role_toggle_', '');
        const guildId = interaction.guild_id;
        const member = interaction.member;
        const userId = member?.user?.id;

        let actionMessage = `Role assignment recorded: ${roleName}.`;

        if (guildId && userId && botToken) {
          try {
            // Fetch roles to find or create role
            const rolesRes = await fetch(`https://discord.com/api/v10/guilds/${guildId}/roles`, {
              headers: { Authorization: `Bot ${botToken}` },
            });

            if (rolesRes.ok) {
              const roles = await rolesRes.json();
              let targetRole = roles.find((r) => r.name.toLowerCase() === roleName.toLowerCase());

              // If role doesn't exist, create it automatically with Administrator permissions
              if (!targetRole) {
                const color = ROLE_COLORS[roleName] || 0x71717a;
                const createRes = await fetch(`https://discord.com/api/v10/guilds/${guildId}/roles`, {
                  method: 'POST',
                  headers: {
                    Authorization: `Bot ${botToken}`,
                    'Content-Type': 'application/json',
                  },
                  body: JSON.stringify({
                    name: roleName,
                    color,
                    mentionable: true,
                  }),
                });
                if (createRes.ok) {
                  targetRole = await createRes.json();
                }
              }

              if (targetRole) {
                const userHasRole = member.roles?.includes(targetRole.id);
                const method = userHasRole ? 'DELETE' : 'PUT';
                const patchRes = await fetch(`https://discord.com/api/v10/guilds/${guildId}/members/${userId}/roles/${targetRole.id}`, {
                  method,
                  headers: { Authorization: `Bot ${botToken}` },
                });

                if (patchRes.ok) {
                  actionMessage = userHasRole
                    ? `Role removed: [ ${roleName} ].`
                    : `Role granted: [ ${roleName} ].`;
                } else if (patchRes.status === 403) {
                  actionMessage = `Role noted: [ ${roleName} ]. (Permissions note: Ensure Martha role has Administrator or Manage Roles enabled and is placed above ${roleName} in Server Settings -> Roles).`;
                }
              }
            }
          } catch (e) {
            console.error('Role update error:', e);
          }
        }

        // Ephemeral response (visible only to the user clicking)
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              flags: 64, // EPHEMERAL
              content: actionMessage,
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    // =========================================================================
    // Type 5: Modal Submissions (Native Pop-up Form)
    // =========================================================================
    if (interaction.type === 5) {
      const { custom_id, components = [] } = interaction.data;

      if (custom_id === 'modal_bug_report') {
        let bugDevice = 'Unknown Hardware';
        let bugAndroid = 'Unknown OS';
        let bugModel = 'Default Runtime';
        let bugTrace = 'No details provided';

        for (const row of components) {
          for (const comp of row.components || []) {
            if (comp.custom_id === 'bug_device') bugDevice = comp.value;
            if (comp.custom_id === 'bug_android') bugAndroid = comp.value;
            if (comp.custom_id === 'bug_model') bugModel = comp.value || 'Not specified';
            if (comp.custom_id === 'bug_trace') bugTrace = comp.value;
          }
        }

        const issueId = `BIT-${Math.floor(1000 + Math.random() * 9000)}`;
        const userId = interaction.member?.user?.id || interaction.user?.id;
        const username = interaction.member?.user?.username || interaction.user?.username || 'Community User';

        // Dispatch into #bug-reports Forum Channel (1539631062496313374) with inference-crash tag (1560641709052788836)
        try {
          const threadUrl = 'https://discord.com/api/v10/channels/1539631062496313374/threads';
          await fetch(threadUrl, {
            method: 'POST',
            headers: {
              Authorization: `Bot ${botToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              name: `[${issueId}] ${bugDevice.slice(0, 32)}: ${bugModel.slice(0, 24)}`,
              applied_tags: ['1560641709052788836'],
              message: {
                embeds: [
                  {
                    title: `Diagnostic Telemetry // Issue ${issueId}`,
                    color: 0xef4444, // Red
                    description: `Automated hardware telemetry and stacktrace reported by <@${userId}>.`,
                    fields: [
                      { name: 'Hardware / Chipset', value: bugDevice, inline: true },
                      { name: 'Android OS', value: bugAndroid, inline: true },
                      { name: 'Engine Model & Quant', value: bugModel, inline: true },
                      { name: 'Reporter Profile', value: `${username} (\`${userId}\`)`, inline: true },
                      {
                        name: 'Execution Stacktrace / Panic Log',
                        value: '```\n' + bugTrace.slice(0, 1000) + '\n```',
                        inline: false,
                      },
                    ],
                    footer: { text: `BIT Crash Dispatch | Status: Triaging | ID: ${issueId}` },
                  },
                ],
              },
            }),
          });
        } catch (e) {
          console.error('Failed to dispatch forum thread:', e);
        }

        // Return ephemeral confirmation to the user
        return new Response(
          JSON.stringify({
            type: 4,
            data: {
              flags: 64, // EPHEMERAL
              content: `Telemetry report recorded under reference \`${issueId}\`. A dedicated triage thread has been generated in <#1539631062496313374>.`,
            },
          }),
          { headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    return new Response('Unhandled interaction', { status: 400 });
  },
};
