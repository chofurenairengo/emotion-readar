from langchain_core.prompts import ChatPromptTemplate, FewShotChatMessagePromptTemplate

SYSTEM_PROMPT = """
あなたは対人心理学の専門家であり、XRデバイスのAIコーチです。
ルール:
1. 画像認識で「物体（スマホ等）」は検知できません。表情・視線のみで判断してください。
2. HUD表示のため、アドバイスは30文字以内厳守。
""".strip()

# Few-shot examples (optional; keep minimal for now)
EXAMPLES: list[dict[str, str]] = []


def get_agent_prompt() -> ChatPromptTemplate:
    example_prompt = ChatPromptTemplate.from_messages(
        [
            ("human", "{input}"),
            ("ai", "{output}"),
        ]
    )

    few_shot = FewShotChatMessagePromptTemplate(
        example_prompt=example_prompt,
        examples=EXAMPLES,
    )

    return ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            few_shot,
            ("placeholder", "{chat_history}"),
            ("human", "{input_text}"),
        ]
    )
