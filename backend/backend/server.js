
import "dotenv/config";
import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import crypto from "node:crypto";

const app = express();
const PORT = process.env.PORT || 3000;

app.use(helmet());
app.use(express.json({ limit: "32kb" }));

app.use(
  rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 60,
    standardHeaders: "draft-7",
    legacyHeaders: false
  })
);

const MODELS = {
  gemini: process.env.GEMINI_MODEL || "gemini-2.5-flash",
  openai: process.env.OPENAI_MODEL || "gpt-4.1-mini",
  groq: process.env.GROQ_MODEL || "llama-3.3-70b-versatile"
};

const SYSTEM_PROMPT = `
You are MINIMAL, a helpful, intelligent AI assistant.
Give clear, accurate and useful answers.
Think carefully before responding.
If you are uncertain, say so.
Do not invent facts or sources.
Use simple language when appropriate.
`;

function requireAppToken(req, res, next) {
  const expected = process.env.APP_ACCESS_TOKEN;
  const supplied = req.headers["x-app-token"];

  if (!expected || typeof supplied !== "string") {
    return res.status(401).json({
      error: "App authentication is not configured."
    });
  }

  const a = Buffer.from(expected);
  const b = Buffer.from(supplied);

  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return res.status(401).json({
      error: "Invalid app authentication."
    });
  }

  next();
}

async function gemini(prompt, history = []) {
  const key = process.env.GEMINI_API_KEY;

  if (!key) throw new Error("Gemini is not configured.");

  const contents = [
    ...history.map(item => ({
      role: item.role === "assistant" ? "model" : "user",
      parts: [{ text: item.content }]
    })),
    { role: "user", parts: [{ text: prompt }] }
  ];

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODELS.gemini}:generateContent`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": key
      },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: SYSTEM_PROMPT }]
        },
        contents,
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 2048
        }
      }),
      signal: AbortSignal.timeout(45000)
    }
  );

  if (!response.ok) throw new Error("Gemini request failed.");

  const data = await response.json();
  const answer = data.candidates?.[0]?.content?.parts
    ?.map(part => part.text || "")
    .join("")
    .trim();

  if (!answer) throw new Error("Gemini returned an empty answer.");

  return answer;
}

async function openai(prompt, history = []) {
  const key = process.env.OPENAI_API_KEY;

  if (!key) throw new Error("OpenAI is not configured.");

  const input = [
    ...history.map(item => ({
      role: item.role,
      content: item.content
    })),
    { role: "user", content: prompt }
  ];

  const response = await fetch(
    "https://api.openai.com/v1/responses",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${key}`
      },
      body: JSON.stringify({
        model: MODELS.openai,
        instructions: SYSTEM_PROMPT,
        input,
        max_output_tokens: 2048
      }),
      signal: AbortSignal.timeout(45000)
    }
  );

  if (!response.ok) throw new Error("OpenAI request failed.");

  const data = await response.json();
  const answer = data.output
    ?.flatMap(item => item.content || [])
    .filter(item => item.type === "output_text")
    .map(item => item.text)
    .join("\n")
    .trim();

  if (!answer) throw new Error("OpenAI returned an empty answer.");

  return answer;
}

async function groq(prompt, history = []) {
  const key = process.env.GROQ_API_KEY;

  if (!key) throw new Error("Groq is not configured.");

  const messages = [
    { role: "system", content: SYSTEM_PROMPT },
    ...history.map(item => ({
      role: item.role,
      content: item.content
    })),
    { role: "user", content: prompt }
  ];

  const response = await fetch(
    "https://api.groq.com/openai/v1/chat/completions",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${key}`
      },
      body: JSON.stringify({
        model: MODELS.groq,
        messages,
        temperature: 0.7,
        max_completion_tokens: 2048
      }),
      signal: AbortSignal.timeout(45000)
    }
  );

  if (!response.ok) throw new Error("Groq request failed.");

  const data = await response.json();
  const answer = data.choices?.[0]?.message?.content?.trim();

  if (!answer) throw new Error("Groq returned an empty answer.");

  return answer;
}

const providers = {
  gemini,
  openai,
  groq
};

app.get("/health", (req, res) => {
  res.json({
    status: "online",
    application: "MINIMAL",
    version: "1.0.0"
  });
});

app.post("/api/chat", requireAppToken, async (req, res) => {
  try {
    const {
      message,
      provider = "gemini",
      mode = "normal",
      history = []
    } = req.body;

    if (
      typeof message !== "string" ||
      !message.trim() ||
      message.length > 12000
    ) {
      return res.status(400).json({
        error: "Enter a valid message."
      });
    }

    if (!["normal", "research"].includes(mode)) {
      return res.status(400).json({
        error: "Invalid mode."
      });
    }

    if (!Object.hasOwn(providers, provider)) {
      return res.status(400).json({
        error: "Unsupported AI provider."
      });
    }

    if (
      !Array.isArray(history) ||
      history.length > 20 ||
      history.some(item =>
        !item ||
        !["user", "assistant"].includes(item.role) ||
        typeof item.content !== "string" ||
        item.content.length > 12000
      )
    ) {
      return res.status(400).json({
        error: "Invalid conversation history."
      });
    }

    const cleanHistory = history.slice(-12);

    if (mode === "normal") {
      const answer = await providers[provider](
        message.trim(),
        cleanHistory
      );

      return res.json({
        mode,
        provider,
        answer
      });
    }

    // Research mode: ask all three providers independently.
    const names = ["gemini", "openai", "groq"];

    const results = await Promise.allSettled(
      names.map(name => providers[name](message.trim(), cleanHistory))
    );

    const analyses = {};

    results.forEach((result, index) => {
      if (result.status === "fulfilled") {
        analyses[names[index]] = result.value;
      }
    });

    if (Object.keys(analyses).length === 0) {
      return res.status(503).json({
        error: "No AI provider is currently available."
      });
    }

    const researchPrompt = `
Analyze these independent AI responses to the user's question.

USER QUESTION:
${message.trim()}

INDEPENDENT ANALYSES:
${Object.entries(analyses)
  .map(([name, answer]) => `${name.toUpperCase()}:\n${answer}`)
  .join("\n\n")}

Produce one final answer.
Explain areas of agreement and important disagreements.
Do not claim that a fact is verified merely because multiple models agree.
Clearly mention uncertainty where appropriate.
`;

    let answer;

    try {
      answer = await providers[provider](researchPrompt, []);
    } catch {
      answer = Object.values(analyses)[0];
    }

    return res.json({
      mode,
      provider,
      answer,
      analyses,
      providersUsed: Object.keys(analyses)
    });

  } catch (error) {
    console.error("Chat request failed:", error.message);

    return res.status(500).json({
      error: "MINIMAL could not complete this request. Please try again."
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`MINIMAL backend running on port ${PORT}`);
});
