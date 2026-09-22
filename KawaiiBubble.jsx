import { useState, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence, useAnimationControls } from "framer-motion";
import { Send, X, Sparkles as SparklesIcon, Heart } from "lucide-react";

/* ---------------------------------------------------------------------- */
/*  Contenu                                                                */
/* ---------------------------------------------------------------------- */

const GREETING = "Konnichiwaaa ! ✨💕 Je peux t'aider ? UwU";

const REPLIES = [
  "Ehehe~ dis-m'en plus, je t'écoute ! (｡•ᴗ•｡) ♡",
  "Uwu je suis trop contente que tu me parles !",
  "Hmm hmm~ je réfléchis très fort pour toi ✨",
  "Oohh intéressant ! Continue, continue !",
  "Kyaa~ j'adore discuter avec toi, tu es trop gentil·le !",
  "Je note ça dans mon petit carnet magique 📔💕",
  "Nyaa~ je suis toujours là pour toi, promis !",
  "Teheh, tu me fais toujours sourire ~ 🌸",
];

/* ---------------------------------------------------------------------- */
/*  Petits hooks                                                          */
/* ---------------------------------------------------------------------- */

function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReduced(mq.matches);
    const handler = (e) => setReduced(e.matches);
    mq.addEventListener?.("change", handler);
    return () => mq.removeEventListener?.("change", handler);
  }, []);
  return reduced;
}

function useBlinking(active) {
  const [blinking, setBlinking] = useState(false);
  useEffect(() => {
    if (!active) return;
    let timeout;
    const schedule = () => {
      const wait = 2600 + Math.random() * 2600;
      timeout = setTimeout(() => {
        setBlinking(true);
        setTimeout(() => setBlinking(false), 130);
        schedule();
      }, wait);
    };
    schedule();
    return () => clearTimeout(timeout);
  }, [active]);
  return blinking;
}

function useOccasionalSmile(active) {
  const [smiling, setSmiling] = useState(false);
  useEffect(() => {
    if (!active) return;
    let timeout;
    const schedule = () => {
      const wait = 3500 + Math.random() * 3500;
      timeout = setTimeout(() => {
        setSmiling(true);
        setTimeout(() => setSmiling(false), 1500);
        schedule();
      }, wait);
    };
    schedule();
    return () => clearTimeout(timeout);
  }, [active]);
  return smiling;
}

/* ---------------------------------------------------------------------- */
/*  Le petit personnage (SVG dessiné à la main, pas d'image externe)      */
/* ---------------------------------------------------------------------- */

function KawaiiFace({ size = 56, smiling, blinking, excited }) {
  const eyeH = blinking ? 2 : excited ? 16 : 13;
  const eyeY = 46 - eyeH / 2;

  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true">
      <defs>
        <radialGradient id="kawaiiFaceGrad" cx="35%" cy="28%" r="80%">
          <stop offset="0%" stopColor="#FFFDFB" />
          <stop offset="100%" stopColor="#FFE7F2" />
        </radialGradient>
      </defs>

      <circle cx="50" cy="53" r="37" fill="url(#kawaiiFaceGrad)" stroke="#F5C9E0" strokeWidth="2" />

      {/* petit noeud kawaii */}
      <circle cx="29" cy="19" r="7" fill="#FF9CC8" />
      <circle cx="40" cy="14" r="5.5" fill="#FF9CC8" />
      <circle cx="34.5" cy="17" r="3" fill="#FFD3E8" />

      {/* joues roses */}
      <ellipse cx="27" cy="59" rx="7" ry="4.5" fill="#FFB6CE" opacity="0.85" />
      <ellipse cx="73" cy="59" rx="7" ry="4.5" fill="#FFB6CE" opacity="0.85" />

      {/* yeux */}
      <rect x="29" y={eyeY} width="11" height={eyeH} rx="5.5" fill="#5B3A52" />
      <rect x="60" y={eyeY} width="11" height={eyeH} rx="5.5" fill="#5B3A52" />
      {!blinking && (
        <>
          <circle cx="32.5" cy={eyeY + eyeH * 0.28} r="2.1" fill="white" />
          <circle cx="63.5" cy={eyeY + eyeH * 0.28} r="2.1" fill="white" />
        </>
      )}

      {/* bouche */}
      {smiling ? (
        <path d="M41 66 Q50 75 59 66" stroke="#C9698C" strokeWidth="2.6" fill="none" strokeLinecap="round" />
      ) : (
        <path d="M45 66.5 Q50 69.5 55 66.5" stroke="#C9698C" strokeWidth="2.4" fill="none" strokeLinecap="round" />
      )}
    </svg>
  );
}

/* ---------------------------------------------------------------------- */
/*  Étincelle décorative                                                  */
/* ---------------------------------------------------------------------- */

function FloatingSparkle({ x, y, kind }) {
  const Icon = kind === "heart" ? Heart : SparklesIcon;
  const color = kind === "heart" ? "#FF8FC0" : "#C9A6FF";
  return (
    <motion.div
      className="absolute pointer-events-none"
      style={{ left: x, top: y, color }}
      initial={{ opacity: 0, scale: 0, y: 0, rotate: 0 }}
      animate={{ opacity: [0, 1, 0], scale: [0.3, 1, 0.7], y: -18, rotate: 90 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 1.4, ease: "easeOut" }}
    >
      <Icon size={13} fill={kind === "heart" ? color : "none"} strokeWidth={1.6} />
    </motion.div>
  );
}

/* ---------------------------------------------------------------------- */
/*  Bulle de message dans le chat                                         */
/* ---------------------------------------------------------------------- */

function ChatBubble({ from, text }) {
  const isUser = from === "user";
  return (
    <motion.div
      initial={{ opacity: 0, y: 10, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: "spring", stiffness: 300, damping: 22 }}
      className={`flex ${isUser ? "justify-end" : "justify-start"}`}
    >
      <div
        className={`max-w-[80%] rounded-3xl px-4 py-2.5 text-sm leading-snug shadow-sm ${
          isUser
            ? "bg-pink-400 text-white rounded-br-md"
            : "bg-white text-pink-900 border border-pink-100 rounded-bl-md"
        }`}
      >
        {text}
      </div>
    </motion.div>
  );
}

function TypingDots() {
  return (
    <div className="flex justify-start">
      <div className="flex items-center gap-1 rounded-3xl rounded-bl-md border border-pink-100 bg-white px-4 py-3 shadow-sm">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="h-1.5 w-1.5 rounded-full bg-pink-300"
            animate={{ y: [0, -4, 0] }}
            transition={{ duration: 0.9, repeat: Infinity, delay: i * 0.15, ease: "easeInOut" }}
          />
        ))}
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/*  Composant principal                                                   */
/* ---------------------------------------------------------------------- */

export default function KawaiiFloatingCompanion() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([{ from: "bot", text: GREETING }]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [sparkles, setSparkles] = useState([]);
  const scrollRef = useRef(null);
  const sparkleId = useRef(0);

  const reducedMotion = usePrefersReducedMotion();
  const blinking = useBlinking(!reducedMotion);
  const smiling = useOccasionalSmile(!reducedMotion);
  const bubbleControls = useAnimationControls();
  const avatarControls = useAnimationControls();

  /* étincelles ambiantes autour de la bulle */
  useEffect(() => {
    if (open || reducedMotion) return;
    const interval = setInterval(() => {
      const id = sparkleId.current++;
      const angle = Math.random() * Math.PI * 2;
      const radius = 34 + Math.random() * 12;
      const kind = Math.random() > 0.55 ? "heart" : "star";
      setSparkles((prev) => [
        ...prev,
        {
          id,
          x: 40 + Math.cos(angle) * radius,
          y: 40 + Math.sin(angle) * radius,
          kind,
        },
      ]);
      setTimeout(() => {
        setSparkles((prev) => prev.filter((s) => s.id !== id));
      }, 1400);
    }, 2200);
    return () => clearInterval(interval);
  }, [open, reducedMotion]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, isTyping]);

  const burstNearAvatar = useCallback(() => {
    const bursts = Array.from({ length: 3 }).map(() => ({
      id: sparkleId.current++,
      x: 10 + Math.random() * 40,
      y: -4 + Math.random() * 10,
      kind: Math.random() > 0.5 ? "heart" : "star",
    }));
    setSparkles((prev) => [...prev, ...bursts]);
    setTimeout(() => {
      const ids = new Set(bursts.map((b) => b.id));
      setSparkles((prev) => prev.filter((s) => !ids.has(s.id)));
    }, 1400);
  }, []);

  const handleBubbleClick = async () => {
    if (!reducedMotion) {
      await bubbleControls.start({
        scaleY: [1, 0.82, 1.18, 0.95, 1],
        scaleX: [1, 1.12, 0.9, 1.03, 1],
        y: [0, 4, -22, 4, 0],
        transition: { duration: 0.55, ease: "easeInOut" },
      });
    }
    setOpen(true);
  };

  const sendMessage = () => {
    const text = input.trim();
    if (!text) return;
    setMessages((prev) => [...prev, { from: "user", text }]);
    setInput("");
    setIsTyping(true);

    const delay = 650 + Math.random() * 700;
    setTimeout(() => {
      const reply = REPLIES[Math.floor(Math.random() * REPLIES.length)];
      setIsTyping(false);
      setMessages((prev) => [...prev, { from: "bot", text: reply }]);
      if (!reducedMotion) {
        avatarControls.start({
          rotate: [0, -6, 6, -3, 0],
          scale: [1, 1.08, 1],
          transition: { duration: 0.5 },
        });
        burstNearAvatar();
      }
    }, delay);
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end font-sans">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Quicksand:wght@500;600;700&display=swap');
        .font-sans { font-family: 'Quicksand', ui-sans-serif, system-ui, sans-serif; }
      `}</style>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, scale: 0.3, y: 50, transformOrigin: "bottom right" }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.3, y: 30 }}
            transition={{ type: "spring", stiffness: 260, damping: 18 }}
            className="mb-4 flex h-[26rem] w-80 flex-col overflow-hidden rounded-3xl border border-white bg-white shadow-2xl sm:w-96"
          >
            {/* En-tête */}
            <div className="relative flex items-center gap-3 bg-gradient-to-r from-pink-300 to-purple-300 px-4 py-3">
              <div className="relative flex h-11 w-11 items-center justify-center rounded-full bg-white bg-opacity-90 shadow-sm">
                <motion.div animate={avatarControls}>
                  <KawaiiFace size={40} smiling={smiling} blinking={blinking} />
                </motion.div>
              </div>
              <div className="flex flex-col leading-tight">
                <span className="text-sm font-semibold text-white">Mochi ✨</span>
                <span className="text-xs text-pink-50 opacity-90">toujours là pour toi ♡</span>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Fermer le chat"
                className="ml-auto rounded-full bg-white bg-opacity-25 p-1.5 text-white transition hover:bg-opacity-40"
              >
                <X size={16} />
              </button>
            </div>

            {/* Messages */}
            <div
              ref={scrollRef}
              className="flex-1 space-y-3 overflow-y-auto bg-gradient-to-b from-pink-50 to-purple-50 px-4 py-4"
            >
              {messages.map((m, i) => (
                <ChatBubble key={i} from={m.from} text={m.text} />
              ))}
              {isTyping && <TypingDots />}
            </div>

            {/* Saisie */}
            <div className="flex items-center gap-2 border-t border-pink-100 bg-white px-3 py-3">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Écris un petit message~"
                aria-label="Message pour Mochi"
                className="flex-1 rounded-full border border-pink-200 bg-pink-50 px-4 py-2 text-sm text-pink-900 placeholder-pink-300 outline-none focus:border-pink-400 focus:ring-2 focus:ring-pink-200"
              />
              <button
                type="button"
                onClick={sendMessage}
                aria-label="Envoyer le message"
                className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-pink-400 text-white shadow-sm transition hover:bg-pink-500 active:scale-95"
              >
                <Send size={15} />
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Bulle flottante */}
      {!open && (
        <motion.button
          type="button"
          onClick={handleBubbleClick}
          aria-label="Ouvrir le chat avec Mochi"
          animate={bubbleControls}
          initial={false}
          className="relative flex h-20 w-20 items-center justify-center rounded-full border border-white bg-white bg-opacity-30 backdrop-blur-xl focus:outline-none focus:ring-4 focus:ring-pink-200"
          style={{
            boxShadow:
              "0 0 30px rgba(255,159,205,0.55), 0 0 60px rgba(201,166,255,0.35), 0 8px 20px rgba(0,0,0,0.08)",
          }}
        >
          <motion.div
            animate={
              reducedMotion
                ? {}
                : { y: [0, -7, 0] }
            }
            transition={{ duration: 2.6, repeat: Infinity, ease: "easeInOut" }}
          >
            <KawaiiFace size={56} smiling={smiling} blinking={blinking} />
          </motion.div>

          {sparkles.map((s) => (
            <FloatingSparkle key={s.id} x={s.x} y={s.y} kind={s.kind} />
          ))}
        </motion.button>
      )}
    </div>
  );
}
