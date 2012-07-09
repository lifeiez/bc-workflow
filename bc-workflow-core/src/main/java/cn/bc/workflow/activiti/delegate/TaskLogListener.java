/**
 * 
 */
package cn.bc.workflow.activiti.delegate;

import java.util.Calendar;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.impl.form.TaskFormHandler;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.task.TaskDefinition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cn.bc.identity.domain.ActorHistory;
import cn.bc.identity.web.SystemContextHolder;
import cn.bc.workflow.domain.ExcutionLog;
import cn.bc.workflow.service.ExcutionLogService;

/**
 * 记录任务日志的监听器
 * 
 * @author dragon
 * 
 */
public class TaskLogListener implements TaskListener {
	private static final Log logger = LogFactory.getLog(TaskLogListener.class);
	private ExcutionLogService excutionLogService;

	@Autowired
	public void setExcutionLogService(ExcutionLogService excutionLogService) {
		this.excutionLogService = excutionLogService;
	}

	public void notify(DelegateTask delegateTask) {
		if (logger.isDebugEnabled()) {
			logger.debug("class=" + delegateTask.getClass());
			logger.debug("id=" + delegateTask.getId());
			logger.debug("eventName=" + delegateTask.getEventName());
			logger.debug("processInstanceId"
					+ delegateTask.getProcessInstanceId());
			logger.debug("executionId=" + delegateTask.getExecutionId());
			logger.debug("taskDefinitionKey="
					+ delegateTask.getTaskDefinitionKey());
		}

		// 创建执行日志
		ExcutionLog log = new ExcutionLog();
		log.setFileDate(Calendar.getInstance());
		ActorHistory h = SystemContextHolder.get().getUserHistory();
		log.setAuthorId(h.getId());
		log.setAuthorCode(h.getCode());
		log.setAuthorName(h.getName());

		log.setListener(delegateTask.getClass().getName());
		log.setExcutionId(delegateTask.getExecutionId());
		log.setType("task_" + delegateTask.getEventName());
		log.setProcessInstanceId(delegateTask.getProcessInstanceId());
		log.setTaskInstanceId(delegateTask.getId());

		// 记录任务的表单key
		if (delegateTask instanceof TaskEntity) {
			TaskEntity task = (TaskEntity) delegateTask;
			TaskDefinition d = task.getTaskDefinition();
			if (d != null) {
				TaskFormHandler fh = d.getTaskFormHandler();
				if (fh != null) {
					TaskFormData fd = fh.createTaskForm(task);
					log.setForm(fd != null ? fd.getFormKey() : "");
				}
			}
		}

		excutionLogService.save(log);
	}
}
