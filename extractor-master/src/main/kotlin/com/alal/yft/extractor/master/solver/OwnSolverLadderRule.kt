package com.alal.yft.extractor.master.solver

/**
 * Phase 1.2 N5a: which step of the solver ladder may give a kind's answer. Pure; the app wires it
 * in N5b, behind ownSolverNext (default off). Flag off, nothing here runs.
 *
 * The ladder runs the worker with strategy P, then the worker with strategy B, then the page
 * (strategy E, see [OwnSolverEnvironmentErrors]). The rule is shared with the computer runners
 * (`scripts/own-solver/ladder.mjs`) through one table of cases,
 * `src/test/resources/own-solver/ladder-rule-cases.json`, that both test suites read:
 *
 * - A worker step answers a kind only when it answered every asked input of that kind and its run
 *   had no init errors (initErrors == 0). An init error means some top-level player code threw,
 *   so the program may be half set up and its answer silently wrong (N4b: worker B answered one
 *   mutated signature wrong after 3 init errors). P comes before B.
 * - The page runs only when a worker step needs it ([needsPage]): an environment failure, an init
 *   error, or answers without an init count. Its answer counts only with no init errors of its
 *   own.
 * - N5b: a signature from any step (worker P, worker B or the page) also needs the live check (V)
 *   of that step's signature to pass ([candidate] names the step to check), because a wrong
 *   signature breaks playback silently while a wrong n only slows it. M9c-nav: worker B gave a
 *   wrong signature with no init error; the environment tripwire (own.solver.core.js) catches it
 *   as an init error, and V is the second line.
 * - Anything else is [Source.NONE]: the kind fails. An answer the rule does not return is never
 *   cached (N5b).
 */
internal object OwnSolverLadderRule {
    enum class Kind { RATE, SIGNATURE }

    /** The live check of the candidate step's signature (V): it played, it did not, or not checked. */
    enum class Live { PASS, FAIL, UNKNOWN }

    /** Where a kind's answer comes from: worker P, worker B, the page (E), or nowhere. */
    enum class Source { P, B, E, NONE }

    /**
     * One run: the kinds it answered for every asked input, its init errors (null: the reply had
     * no count) and its failure classes. Kinds, counts and classes only, never a value.
     */
    data class Step(
        val answered: Set<Kind>,
        val initErrors: Int?,
        val failures: List<String> = emptyList(),
    )

    private val WORKER_SOURCES = listOf(Source.P, Source.B)

    /** Whether this worker step sends the run on to the page. */
    fun needsPage(step: Step): Boolean =
        step.failures.any { OwnSolverEnvironmentErrors.isEnvironmentFailure(it) } ||
            (step.initErrors ?: 0) > 0 ||
            (step.initErrors == null && step.answered.isNotEmpty())

    /**
     * The source of [kind]'s answer. [workers] are the worker steps in ladder order (P, then B; a
     * step that did not run is left off the end), [page] is the page step (null: not run).
     */
    fun decide(kind: Kind, workers: List<Step>, page: Step?, live: Live): Source {
        val source = candidate(kind, workers, page)
        return when {
            source == Source.NONE -> Source.NONE
            kind == Kind.RATE -> source
            live == Live.PASS -> source
            else -> Source.NONE
        }
    }

    /**
     * The step whose answer [decide] takes before V: a signature from it still needs V, run on
     * that step's signature.
     */
    fun candidate(kind: Kind, workers: List<Step>, page: Step?): Source {
        require(workers.size <= WORKER_SOURCES.size) { "At most two worker steps (P, B)" }
        workers.forEachIndexed { index, step ->
            if (step.initErrors == 0 && kind in step.answered) return WORKER_SOURCES[index]
        }
        val pageAnswers = page != null && workers.any { needsPage(it) } &&
            page.initErrors == 0 && kind in page.answered
        return if (pageAnswers) Source.E else Source.NONE
    }

    /** A decoded reply as a step: a kind is answered when every asked input of it has a value. */
    fun step(decoded: OwnSolverProtocol.Decoded, batch: OwnSolverProtocol.Batch): Step = when (decoded) {
        is OwnSolverProtocol.Decoded.Failed -> Step(emptySet(), null, listOf(decoded.reason))
        is OwnSolverProtocol.Decoded.Solved -> Step(
            answered = buildSet {
                if (answersAll(decoded.rate, batch.rateInputs)) add(Kind.RATE)
                if (answersAll(decoded.signature, batch.signatureInputs)) add(Kind.SIGNATURE)
            },
            initErrors = decoded.initErrors,
            failures = decoded.failures,
        )
    }

    private fun answersAll(values: Map<String, String>, inputs: List<String>): Boolean =
        inputs.isNotEmpty() && inputs.all { values[it]?.isNotEmpty() == true }
}
