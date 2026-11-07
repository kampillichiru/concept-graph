package com.conceptgraph.lessons.poc05;

import com.conceptgraph.evals.RetrievalEval;
import com.conceptgraph.evals.RetrievalEval.Method;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * POC 5, lesson 5: measure every method on the 20-question golden set.
 * GET /poc05/eval  hit@1, hit@3, MRR@10 and latency per method, plus a per-question breakdown
 */
@RestController
@RequestMapping("/poc05")
public class Poc05EvalController {

    private final RetrievalEval eval;

    public Poc05EvalController(RetrievalEval eval) {
        this.eval = eval;
    }

    @Tag(name = "POC 5.5 Retrieval eval", description = "Golden set: vector vs BM25 vs hybrid vs hybrid+rerank")
    @Operation(summary = "Run the retrieval eval",
            description = "VECTOR, BM25 and HYBRID take seconds. HYBRID_RERANK and REWRITE_HYBRID make one LLM call "
                    + "per question: several minutes on a CPU. Start with methods=VECTOR,BM25,HYBRID. "
                    + "Read summary.*.hit@3ByKind: which method wins on 'exact', which on 'paraphrase'? "
                    + "Then read the rows where methods disagree. 'markdown' is the table for the lesson page.")
    @GetMapping("/eval")
    public Map<String, Object> run(
            @RequestParam(defaultValue = "VECTOR,BM25,HYBRID,HYBRID_RERANK") List<Method> methods) {
        return eval.run(methods);
    }
}
