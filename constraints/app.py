from functools import lru_cache
import re

import numpy as np
from fastapi import FastAPI
from fastembed import TextEmbedding
from pydantic import BaseModel, Field

app = FastAPI()
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
EXAMPLES = {
    "PAIR_RELATION": ["두 선수를 같은 팀에 넣어줘", "두 선수를 다른 팀에 넣어줘", "두 사람이 함께 뛰게 해줘", "둘을 각각 다른 팀에 배치해줘"],
    "POSITION_BALANCE": ["포지션을 골고루 나눠줘", "각 팀의 포지션 균형을 맞춰줘", "같은 포지션 선수가 한 팀에 몰리지 않게 해줘", "LWB, MF, LW, ST를 팀마다 고르게 나눠줘", "포인트가드, 센터, 스몰포워드가 한 팀에 몰리지 않게 해줘"],
}
POSITION_TERMS = re.compile(r"포지션|가드|센터|포워드|윙|미드필더|수비|공격|\b(?:LWB|RWB|LB|RB|CB|GK|DF|MF|CM|CDM|CAM|LM|RM|LW|RW|ST|CF|FW|PG|SG|SF|PF)\b", re.IGNORECASE)


class ClassifyRequest(BaseModel):
    clauses: list[str] = Field(min_length=1, max_length=5)


@lru_cache(maxsize=1)
def model_and_examples():
    model = TextEmbedding(model_name=MODEL_NAME, cache_dir="/models")
    labels = [label for label, examples in EXAMPLES.items() for _ in examples]
    examples = [text for group in EXAMPLES.values() for text in group]
    vectors = np.stack(list(model.embed(examples)))
    vectors /= np.linalg.norm(vectors, axis=1, keepdims=True)
    return model, labels, vectors


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/classify")
def classify(request: ClassifyRequest):
    model, labels, examples = model_and_examples()
    queries = np.stack(list(model.embed(request.clauses)))
    queries /= np.linalg.norm(queries, axis=1, keepdims=True)
    scores = queries @ examples.T
    output = []
    for clause, row in zip(request.clauses, scores):
        by_label = {label: max(float(row[i]) for i, candidate in enumerate(labels) if candidate == label)
                    for label in EXAMPLES}
        if POSITION_TERMS.search(clause) and by_label["POSITION_BALANCE"] >= 0.80:
            kind = "POSITION_BALANCE"
            score = by_label[kind]
        elif by_label["PAIR_RELATION"] >= 0.75:
            kind = "PAIR_RELATION"
            score = by_label[kind]
        else:
            kind = "UNKNOWN"
            score = max(by_label.values())
        output.append({"type": kind, "score": score})
    return {"results": output}
