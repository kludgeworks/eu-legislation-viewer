package eu.legislation.formex.ingester.listener

import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

@Component
class NoWorkFoundStepExecutionListener : StepExecutionListener {

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        if (stepExecution.readCount == 0L) {
            return ExitStatus.FAILED
        }
        return stepExecution.exitStatus
    }
}
