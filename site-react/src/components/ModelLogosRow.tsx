import React from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ease } from '../lib/motion';

// 1. DeepSeek: Exact SVG from user
export const DeepSeekIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-label="DeepSeek"
  >
    <title>DeepSeek</title>
    <path
      d="M23.748 4.482c-.254-.124-.364.113-.512.234-.051.039-.094.09-.137.136-.372.397-.806.657-1.373.626-.829-.046-1.537.214-2.163.848-.133-.782-.575-1.248-1.247-1.548-.352-.156-.708-.311-.955-.65-.172-.241-.219-.51-.305-.774-.055-.16-.11-.323-.293-.35-.2-.031-.278.136-.356.276-.313.572-.434 1.202-.422 1.84.027 1.436.633 2.58 1.838 3.393.137.093.172.187.129.323-.082.28-.18.552-.266.833-.055.179-.137.217-.329.14a5.526 5.526 0 01-1.736-1.18c-.857-.828-1.631-1.742-2.597-2.458a11.365 11.365 0 00-.689-.471c-.985-.957.13-1.743.388-1.836.27-.098.093-.432-.779-.428-.872.004-1.67.295-2.687.684a3.055 3.055 0 01-.465.137 9.597 9.597 0 00-2.883-.102c-1.885.21-3.39 1.102-4.497 2.623C.082 8.606-.231 10.684.152 12.85c.403 2.284 1.569 4.175 3.36 5.653 1.858 1.533 3.997 2.284 6.438 2.14 1.482-.085 3.133-.284 4.994-1.86.47.234.962.327 1.78.397.63.059 1.236-.03 1.705-.128.735-.156.684-.837.419-.961-2.155-1.004-1.682-.595-2.113-.926 1.096-1.296 2.746-2.642 3.392-7.003.05-.347.007-.565 0-.845-.004-.17.035-.237.23-.256a4.173 4.173 0 001.545-.475c1.396-.763 1.96-2.015 2.093-3.517.02-.23-.004-.467-.247-.588zM11.581 18c-2.089-1.642-3.102-2.183-3.52-2.16-.392.024-.321.471-.235.763.09.288.207.486.371.739.114.167.192.416-.113.603-.673.416-1.842-.14-1.897-.167-1.361-.802-2.5-1.86-3.301-3.307-.774-1.393-1.224-2.887-1.298-4.482-.02-.386.093-.522.477-.592a4.696 4.696 0 011.529-.039c2.132.312 3.946 1.265 5.468 2.774.868.86 1.525 1.887 2.202 2.891.72 1.066 1.494 2.082 2.48 2.914.348.292.625.514.891.677-.802.09-2.14.11-3.054-.614zm1-6.44a.306.306 0 01.415-.287.302.302 0 01.2.288.306.306 0 01-.31.307.303.303 0 01-.304-.308zm3.11 1.596c-.2.081-.399.151-.59.16a1.245 1.245 0 01-.798-.254c-.274-.23-.47-.358-.552-.758a1.73 1.73 0 01.016-.588c.07-.327-.008-.537-.239-.727-.187-.156-.426-.199-.688-.199a.559.559 0 01-.254-.078c-.11-.054-.2-.19-.114-.358.028-.054.16-.186.192-.21.356-.202.767-.136 1.146.016.352.144.618.408 1.001.782.391.451.462.576.685.914.176.265.336.537.445.848.067.195-.019.354-.25.452z"
      fill="#4D6BFE"
    />
  </svg>
);

// 2. Qwen: Exact SVG from user
export const QwenIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    aria-label="Qwen"
  >
    <title>Qwen</title>
    <path
      d="M12.604 1.34c.393.69.784 1.382 1.174 2.075a.18.18 0 00.157.091h5.552c.174 0 .322.11.446.327l1.454 2.57c.19.337.24.478.024.837-.26.43-.513.864-.76 1.3l-.367.658c-.106.196-.223.28-.04.512l2.652 4.637c.172.301.111.494-.043.77-.437.785-.882 1.564-1.335 2.34-.159.272-.352.375-.68.37-.777-.016-1.552-.01-2.327.016a.099.099 0 00-.081.05 575.097 575.097 0 01-2.705 4.74c-.169.293-.38.363-.725.364-.997.003-2.002.004-3.017.002a.537.537 0 01-.465-.271l-1.335-2.323a.09.09 0 00-.083-.049H4.982c-.285.03-.553-.001-.805-.092l-1.603-2.77a.543.543 0 01-.002-.54l1.207-2.12a.198.198 0 000-.197 550.951 550.951 0 01-1.875-3.272l-.79-1.395c-.16-.31-.173-.496.095-.965.465-.813.927-1.625 1.387-2.436.132-.234.304-.334.584-.335a338.3 338.3 0 012.589-.001.124.124 0 00.107-.063l2.806-4.895a.488.488 0 01.422-.246c.524-.001 1.053 0 1.583-.006L11.704 1c.341-.003.724.032.9.34zm-3.432.403a.06.06 0 00-.052.03L6.254 6.788a.157.157 0 01-.135.078H3.253c-.056 0-.07.025-.041.074l5.81 10.156c.025.042.013.062-.034.063l-2.795.015a.218.218 0 00-.2.116l-1.32 2.31c-.044.078-.021.118.068.118l5.716.008c.046 0 .08.02.104.061l1.403 2.454c.046.081.092.082.139 0l5.006-8.76.783-1.382a.055.055 0 01.096 0l1.424 2.53a.122.122 0 00.107.062l2.763-.02a.04.04 0 00.035-.02.041.041 0 000-.04l-2.9-5.086a.108.108 0 010-.113l.293-.507 1.12-1.977c.024-.041.012-.062-.035-.062H9.2c-.059 0-.073-.026-.043-.077l1.434-2.505a.107.107 0 000-.114L9.225 1.774a.06.06 0 00-.053-.031zm6.29 8.02c.046 0 .058.02.034.06l-.832 1.465-2.613 4.585a.056.056 0 01-.05.029.058.058 0 01-.05-.029L8.498 9.841c-.02-.034-.01-.052.028-.054l.216-.012 6.722-.012z"
      fill="url(#lobe-icons-qwen-grad)"
      fillRule="nonzero"
    />
    <defs>
      <linearGradient id="lobe-icons-qwen-grad" x1="0%" x2="100%" y1="0%" y2="0%">
        <stop offset="0%" stopColor="#6336E7" stopOpacity="0.84" />
        <stop offset="100%" stopColor="#6F69F7" stopOpacity="0.84" />
      </linearGradient>
    </defs>
  </svg>
);

// 3. Microsoft Phi: Exact SVG from user
export const MicrosoftPhiIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    aria-label="Microsoft Phi"
  >
    <title>Microsoft Phi</title>
    <path d="M11.49 2H2v9.492h9.492V2h-.002z" fill="#F25022" />
    <path d="M22 2h-9.492v9.492H22V2z" fill="#7FBA00" />
    <path d="M11.49 12.508H2V22h9.492v-9.492h-.002z" fill="#00A4EF" />
    <path d="M22 12.508h-9.492V22H22v-9.492z" fill="#FFB900" />
  </svg>
);

// 4. Meta Llama: Exact SVG from user
export const MetaIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    aria-label="Meta"
  >
    <title>Meta</title>
    <path d="M6.897 4h-.024l-.031 2.615h.022c1.715 0 3.046 1.357 5.94 6.246l.175.297.012.02 1.62-2.438-.012-.019a48.763 48.763 0 00-1.098-1.716 28.01 28.01 0 00-1.175-1.629C10.413 4.932 8.812 4 6.896 4z" fill="url(#lobe-icons-meta-0)" />
    <path d="M6.873 4C4.95 4.01 3.247 5.258 2.02 7.17a4.352 4.352 0 00-.01.017l2.254 1.231.011-.017c.718-1.083 1.61-1.774 2.568-1.785h.021L6.896 4h-.023z" fill="url(#lobe-icons-meta-1)" />
    <path d="M2.019 7.17l-.011.017C1.2 8.447.598 9.995.274 11.664l-.005.022 2.534.6.004-.022c.27-1.467.786-2.828 1.456-3.845l.011-.017L2.02 7.17z" fill="url(#lobe-icons-meta-2)" />
    <path d="M2.807 12.264l-2.533-.6-.005.022c-.177.918-.267 1.851-.269 2.786v.023l2.598.233v-.023a12.591 12.591 0 01.21-2.44z" fill="url(#lobe-icons-meta-3)" />
    <path d="M2.677 15.537a5.462 5.462 0 01-.079-.813v-.022L0 14.468v.024a8.89 8.89 0 00.146 1.652l2.535-.585a4.106 4.106 0 01-.004-.022z" fill="url(#lobe-icons-meta-4)" />
    <path d="M3.27 16.89c-.284-.31-.484-.756-.589-1.328l-.004-.021-2.535.585.004.021c.192 1.01.568 1.85 1.106 2.487l.014.017 2.018-1.745a2.106 2.106 0 01-.015-.016z" fill="url(#lobe-icons-meta-5)" />
    <path d="M10.78 9.654c-1.528 2.35-2.454 3.825-2.454 3.825-2.035 3.2-2.739 3.917-3.871 3.917a1.545 1.545 0 01-1.186-.508l-2.017 1.744.014.017C2.01 19.518 3.058 20 4.356 20c1.963 0 3.374-.928 5.884-5.33l1.766-3.13a41.283 41.283 0 00-1.227-1.886z" fill="#0082FB" />
    <path d="M13.502 5.946l-.016.016c-.4.43-.786.908-1.16 1.416.378.483.768 1.024 1.175 1.63.48-.743.928-1.345 1.367-1.807l.016-.016-1.382-1.24z" fill="url(#lobe-icons-meta-6)" />
    <path d="M20.918 5.713C19.853 4.633 18.583 4 17.225 4c-1.432 0-2.637.787-3.723 1.944l-.016.016 1.382 1.24.016-.017c.715-.747 1.408-1.12 2.176-1.12.826 0 1.6.39 2.27 1.075l.015.016 1.589-1.425-.016-.016z" fill="#0082FB" />
    <path d="M23.998 14.125c-.06-3.467-1.27-6.566-3.064-8.396l-.016-.016-1.588 1.424.015.016c1.35 1.392 2.277 3.98 2.361 6.971v.023h2.292v-.022z" fill="url(#lobe-icons-meta-7)" />
    <path d="M23.998 14.15v-.023h-2.292v.022c.004.14.006.282.006.424 0 .815-.121 1.474-.368 1.95l-.011.022 1.708 1.782.013-.02c.62-.96.946-2.293.946-3.91 0-.083 0-.165-.002-.247z" fill="url(#lobe-icons-meta-8)" />
    <path d="M21.344 16.52l-.011.02c-.214.402-.519.67-.917.787l.778 2.462a3.493 3.493 0 00.438-.182 3.558 3.558 0 001.366-1.218l.044-.065.012-.02-1.71-1.784z" fill="url(#lobe-icons-meta-9)" />
    <path d="M19.92 17.393c-.262 0-.492-.039-.718-.14l-.798 2.522c.449.153.927.222 1.46.222.492 0 .943-.073 1.352-.215l-.78-2.462c-.167.05-.341.075-.517.073z" fill="url(#lobe-icons-meta-10)" />
    <path d="M18.323 16.534l-.014-.017-1.836 1.914.016.017c.637.682 1.246 1.105 1.937 1.337l.797-2.52c-.291-.125-.573-.353-.9-.731z" fill="url(#lobe-icons-meta-11)" />
    <path d="M18.309 16.515c-.55-.642-1.232-1.712-2.303-3.44l-1.396-2.336-.011-.02-1.62 2.438.012.02.989 1.668c.959 1.61 1.74 2.774 2.493 3.585l.016.016 1.834-1.914a2.353 2.353 0 01-.014-.017z" fill="url(#lobe-icons-meta-12)" />
    <defs>
      <linearGradient id="lobe-icons-meta-0" x1="75.897%" x2="26.312%" y1="89.199%" y2="12.194%"><stop offset=".06%" stopColor="#0867DF" /><stop offset="45.39%" stopColor="#0668E1" /><stop offset="85.91%" stopColor="#0064E0" /></linearGradient>
      <linearGradient id="lobe-icons-meta-1" x1="21.67%" x2="97.068%" y1="75.874%" y2="23.985%"><stop offset="13.23%" stopColor="#0064DF" /><stop offset="99.88%" stopColor="#0064E0" /></linearGradient>
      <linearGradient id="lobe-icons-meta-2" x1="38.263%" x2="60.895%" y1="89.127%" y2="16.131%"><stop offset="1.47%" stopColor="#0072EC" /><stop offset="68.81%" stopColor="#0064DF" /></linearGradient>
      <linearGradient id="lobe-icons-meta-3" x1="47.032%" x2="52.15%" y1="90.19%" y2="15.745%"><stop offset="7.31%" stopColor="#007CF6" /><stop offset="99.43%" stopColor="#0072EC" /></linearGradient>
      <linearGradient id="lobe-icons-meta-4" x1="52.155%" x2="47.591%" y1="58.301%" y2="37.004%"><stop offset="7.31%" stopColor="#007FF9" /><stop offset="100%" stopColor="#007CF6" /></linearGradient>
      <linearGradient id="lobe-icons-meta-5" x1="37.689%" x2="61.961%" y1="12.502%" y2="63.624%"><stop offset="7.31%" stopColor="#007FF9" /><stop offset="100%" stopColor="#0082FB" /></linearGradient>
      <linearGradient id="lobe-icons-meta-6" x1="34.808%" x2="62.313%" y1="68.859%" y2="23.174%"><stop offset="27.99%" stopColor="#007FF8" /><stop offset="91.41%" stopColor="#0082FB" /></linearGradient>
      <linearGradient id="lobe-icons-meta-7" x1="43.762%" x2="57.602%" y1="6.235%" y2="98.514%"><stop offset="0%" stopColor="#0082FB" /><stop offset="99.95%" stopColor="#0081FA" /></linearGradient>
      <linearGradient id="lobe-icons-meta-8" x1="60.055%" x2="39.88%" y1="4.661%" y2="69.077%"><stop offset="6.19%" stopColor="#0081FA" /><stop offset="100%" stopColor="#0080F9" /></linearGradient>
      <linearGradient id="lobe-icons-meta-9" x1="30.282%" x2="61.081%" y1="59.32%" y2="33.244%"><stop offset="0%" stopColor="#027AF3" /><stop offset="100%" stopColor="#0080F9" /></linearGradient>
      <linearGradient id="lobe-icons-meta-10" x1="20.433%" x2="82.112%" y1="50.001%" y2="50.001%"><stop offset="0%" stopColor="#0377EF" /><stop offset="99.94%" stopColor="#0279F1" /></linearGradient>
      <linearGradient id="lobe-icons-meta-11" x1="40.303%" x2="72.394%" y1="35.298%" y2="57.811%"><stop offset=".19%" stopColor="#0471E9" /><stop offset="100%" stopColor="#0377EF" /></linearGradient>
      <linearGradient id="lobe-icons-meta-12" x1="32.254%" x2="68.003%" y1="19.719%" y2="84.908%"><stop offset="27.65%" stopColor="#0867DF" /><stop offset="100%" stopColor="#0471E9" /></linearGradient>
    </defs>
  </svg>
);

// 5. Mistral: Exact SVG from user
export const MistralIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    aria-label="Mistral"
  >
    <title>Mistral</title>
    <path d="M3.428 3.4h3.429v3.428H3.428V3.4zm13.714 0h3.43v3.428h-3.43V3.4z" fill="#FFD700" />
    <path d="M3.428 6.828h6.857v3.429H3.429V6.828zm10.286 0h6.857v3.429h-6.857V6.828z" fill="#FFAF00" />
    <path d="M3.428 10.258h17.144v3.428H3.428v-3.428z" fill="#FF8205" />
    <path d="M3.428 13.686h3.429v3.428H3.428v-3.428zm6.858 0h3.429v3.428h-3.429v-3.428zm6.856 0h3.43v3.428h-3.43v-3.428z" fill="#FA500F" />
    <path d="M0 17.114h10.286v3.429H0v-3.429zm13.714 0H24v3.429H13.714v-3.429z" fill="#E10500" />
  </svg>
);

// 6. Google Gemma: Official Google G mark
export const GemmaIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => (
  <svg
    viewBox="0 0 24 24"
    className={className}
    xmlns="http://www.w3.org/2000/svg"
    aria-label="Google Gemma"
  >
    <title>Google Gemma</title>
    <path
      fill="#4285F4"
      d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
    />
    <path
      fill="#34A853"
      d="M12 24c3.24 0 6-1.07 8-2.91l-3.88-3.05c-1.08.72-2.46 1.16-4.12 1.16-3.13 0-5.78-2.11-6.73-4.96H1.24v3.15C3.26 21.36 7.34 24 12 24z"
    />
    <path
      fill="#FBBC05"
      d="M5.27 14.24c-.25-.72-.38-1.49-.38-2.24s.13-1.52.38-2.24V6.61H1.24C.45 8.18 0 9.95 0 12c0 2.05.45 3.82 1.24 5.39l4.03-3.15z"
    />
    <path
      fill="#EA4335"
      d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.96 1.19 15.24 0 12 0 7.34 0 3.26 2.64 1.24 6.61l4.03 3.15c.95-2.85 3.6-4.96 6.73-4.96z"
    />
  </svg>
);

interface ModelBrand {
  name: string;
  label: string;
  component: React.FC<{ className?: string }>;
}

const BRANDS: ModelBrand[] = [
  { name: 'DeepSeek', label: 'DeepSeek', component: DeepSeekIcon },
  { name: 'Qwen', label: 'Qwen', component: QwenIcon },
  { name: 'Microsoft Phi', label: 'Microsoft Phi', component: MicrosoftPhiIcon },
  { name: 'Meta Llama', label: 'Meta Llama', component: MetaIcon },
  { name: 'Mistral AI', label: 'Mistral AI', component: MistralIcon },
  { name: 'Google Gemma', label: 'Gemma', component: GemmaIcon },
];

export const ModelLogosRow: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();

  return (
    <section className="relative w-full bg-white border-b border-[var(--line)] py-12 sm:py-14 overflow-hidden">
      <div className="container">
        
        {/* Eyebrow Label on a single crisp line */}
        <div className="text-center mb-8 sm:mb-9">
          <span className="text-[11px] sm:text-xs font-mono font-medium tracking-[0.22em] text-[#6b6b6b] uppercase select-none whitespace-nowrap">
            Runs Real Open-Weight Foundation Models Locally
          </span>
        </div>

        {/* Logo Strip: Desktop single balanced row with user-provided exact SVGs */}
        <div className="flex flex-wrap md:flex-nowrap items-center justify-center md:justify-between gap-x-6 sm:gap-x-8 md:gap-x-10 gap-y-6 max-w-6xl mx-auto px-4">
          {BRANDS.map((brand, idx) => {
            const IconComp = brand.component;
            return (
              <motion.div
                key={brand.name}
                initial={shouldReduceMotion ? false : { opacity: 0, y: 8 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.6 }}
                transition={{ duration: 0.45, delay: idx * 0.05, ease }}
                className="group relative flex items-center gap-2.5 sm:gap-3 cursor-default shrink-0 px-2.5 py-1.5 rounded-lg transition-all duration-300 hover:bg-black/[0.03]"
                title={brand.name}
              >
                <div className="transition-transform duration-300 group-hover:scale-110 flex items-center justify-center">
                  <IconComp className="w-5 h-5 sm:w-6 sm:h-6 shrink-0" />
                </div>
                <span className="font-semibold text-[#0a0a0a] text-[14px] sm:text-[15px] tracking-tight group-hover:text-black transition-colors">
                  {brand.label}
                </span>
              </motion.div>
            );
          })}
        </div>

      </div>
    </section>
  );
};

export default ModelLogosRow;
