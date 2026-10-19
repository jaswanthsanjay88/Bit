/**
 * BIT Discord Gateway Bot (WebSocket Mode)
 * Keeps the bot ONLINE with a green circle 24/7
 */

import { Client, GatewayIntentBits, ActivityType, EmbedBuilder } from 'discord.js';

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

const TOKEN = process.env.DISCORD_TOKEN;
if (!TOKEN) {
  console.error('Missing DISCORD_TOKEN environment variable.');
  process.exit(1);
}

client.once('ready', () => {
  console.log(`✅ Logged in as ${client.user.tag}!`);
  client.user.setActivity('BIT AI Assistant | /help', { type: ActivityType.Watching });
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  const { commandName } = interaction;

  if (commandName === 'ping') {
    await interaction.reply({ content: `🏓 Pong! Latency: ${client.ws.ping}ms` });
  } else if (commandName === 'about') {
    const embed = new EmbedBuilder()
      .setTitle('⚡ About BIT')
      .setDescription('**BIT** is an open-source, 100% offline, privacy-first AI assistant and autonomous agent execution harness for Android.')
      .setColor(0x6366f1)
      .addFields(
        { name: '🧠 On-Device LLMs', value: 'Native llama.cpp runtime supporting GGUF models.' },
        { name: '🎙️ Voice Pipeline', value: 'Whisper STT + Piper & Kokoro neural TTS.' },
        { name: '🛡️ Privacy & Sandbox', value: 'Zero telemetry, encrypted memory vault & PRoot Linux.' }
      );
    await interaction.reply({ embeds: [embed] });
  } else if (commandName === 'links') {
    const embed = new EmbedBuilder()
      .setTitle('🔗 Official BIT Links')
      .setColor(0x10b981)
      .addFields(
        { name: '🌐 Website', value: '[bit.jaswanthsanjay.me](https://bit.jaswanthsanjay.me)', inline: true },
        { name: '🐙 GitHub', value: '[github.com/jaswanthsanjay88/Bit](https://github.com/jaswanthsanjay88/Bit)', inline: true },
        { name: '💬 Discord', value: '[discord.gg/v75PxaJDy](https://discord.gg/v75PxaJDy)', inline: true }
      );
    await interaction.reply({ embeds: [embed] });
  } else if (commandName === 'models') {
    const embed = new EmbedBuilder()
      .setTitle('📱 Recommended On-Device GGUF Models')
      .setColor(0xf59e0b)
      .addFields(
        { name: 'Qwen 2.5 (1.5B / 3B)', value: 'Best for general reasoning, tool calling, and chat.' },
        { name: 'Llama 3.2 (1B / 3B)', value: 'Fast generation speeds on mid-range Android devices.' },
        { name: 'Gemma 2 (2B)', value: 'High precision and concise answers.' }
      );
    await interaction.reply({ embeds: [embed] });
  } else if (commandName === 'help') {
    const embed = new EmbedBuilder()
      .setTitle('📖 BIT Bot Commands')
      .setColor(0x3b82f6)
      .setDescription('Available commands: `/about`, `/links`, `/models`, `/ping`, `/help`');
    await interaction.reply({ embeds: [embed] });
  }
});

client.login(TOKEN);
