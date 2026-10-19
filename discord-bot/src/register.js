/**
 * Professional Discord Command Registry
 * Registers clean, duplicate-free commands with options, modals, and components
 */

const APPLICATION_ID = '1560841802590724136';
const token = process.argv[2] || process.env.DISCORD_TOKEN;
if (!token) {
  console.error('Missing DISCORD_TOKEN. Pass it as an argument: node register.js <TOKEN>');
  process.exit(1);
}

const commands = [
  {
    name: 'canirun',
    description: 'Hardware compatibility and memory sizing evaluation for Android devices',
    options: [
      {
        name: 'ram',
        description: 'Physical RAM capacity of the target device',
        type: 3, // STRING
        required: true,
        choices: [
          { name: '4 GB RAM', value: '4' },
          { name: '6 GB RAM', value: '6' },
          { name: '8 GB RAM', value: '8' },
          { name: '12 GB RAM', value: '12' },
          { name: '16+ GB RAM', value: '16' },
        ],
      },
      {
        name: 'processor',
        description: 'Processor generation and performance tier',
        type: 3, // STRING
        required: false,
        choices: [
          { name: 'Flagship (Snapdragon 8 Gen 2/3, Dimensity 9300+)', value: 'flagship' },
          { name: 'Mid-Range (Snapdragon 7 Series, Dimensity 7000/8000)', value: 'mid' },
          { name: 'Entry / Budget Tier', value: 'entry' },
        ],
      },
    ],
  },
  {
    name: 'download',
    description: 'Retrieve latest build artifacts, direct APK action buttons, and release manifests',
  },
  {
    name: 'models',
    description: 'Interactive quantization matrix and on-device runtime specifications',
  },
  {
    name: 'bug',
    description: 'Launch native diagnostic form to report a bug or crash to #bug-reports',
  },
  {
    name: 'roles',
    description: 'Deploy 1-click self-service developer and tester role panel',
  },
  {
    name: 'about',
    description: 'Core engine architecture and security boundaries',
  },
  {
    name: 'links',
    description: 'Official repository, web portal, and distribution channels',
  },
  {
    name: 'ping',
    description: 'Edge node diagnostic and round-trip latency',
  },
];

async function syncCommands() {
  console.log(`Registering ${commands.length} professional commands to Guild: ${GUILD_ID}...`);

  const url = `https://discord.com/api/v10/applications/${APPLICATION_ID}/guilds/${GUILD_ID}/commands`;

  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      Authorization: `Bot ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(commands),
  });

  if (response.ok) {
    const data = await response.json();
    console.log(`Successfully synced ${data.length} commands to guild!`);
  } else {
    console.error(`Failed to register: ${response.status}`, await response.text());
  }
}

syncCommands();
