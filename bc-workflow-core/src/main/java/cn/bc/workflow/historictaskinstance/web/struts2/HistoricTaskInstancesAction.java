package cn.bc.workflow.historictaskinstance.web.struts2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.impl.persistence.entity.SuspensionState;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import cn.bc.BCConstants;
import cn.bc.core.query.condition.Condition;
import cn.bc.core.query.condition.Direction;
import cn.bc.core.query.condition.impl.AndCondition;
import cn.bc.core.query.condition.impl.IsNotNullCondition;
import cn.bc.core.query.condition.impl.IsNullCondition;
import cn.bc.core.query.condition.impl.OrderCondition;
import cn.bc.core.util.DateUtils;
import cn.bc.db.jdbc.RowMapper;
import cn.bc.db.jdbc.SqlObject;
import cn.bc.identity.web.SystemContext;
import cn.bc.option.domain.OptionItem;
import cn.bc.web.formater.AbstractFormater;
import cn.bc.web.formater.CalendarFormater;
import cn.bc.web.formater.EntityStatusFormater;
import cn.bc.web.ui.html.grid.Column;
import cn.bc.web.ui.html.grid.HiddenColumn4MapKey;
import cn.bc.web.ui.html.grid.IdColumn4MapKey;
import cn.bc.web.ui.html.grid.TextColumn4MapKey;
import cn.bc.web.ui.html.page.PageOption;
import cn.bc.web.ui.html.toolbar.Toolbar;
import cn.bc.web.ui.html.toolbar.ToolbarButton;
import cn.bc.web.ui.json.Json;
import cn.bc.workflow.historictaskinstance.service.HistoricTaskInstanceService;
import cn.bc.workflow.service.WorkspaceServiceImpl;
import cn.bc.workflow.web.struts2.ViewAction;

/**
 * 经办、任务监控视图Action
 * 
 * @author lbj
 * 
 */

@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Controller
public class HistoricTaskInstancesAction extends
		ViewAction<Map<String, Object>> {
	private static final long serialVersionUID = 1L;
	public String status ;
	
	protected HistoricTaskInstanceService historicTaskInstanceService;

	@Autowired
	public void setHistoricTaskInstanceService(
			HistoricTaskInstanceService historicTaskInstanceService) {
		this.historicTaskInstanceService = historicTaskInstanceService;
	}

	@Override
	public boolean isReadonly() {
		SystemContext context = (SystemContext) this.getContext();
		// 配置权限：、超级管理员
		return !context.hasAnyRole(getText("key.role.bc.admin"),
				getText("key.role.bc.workflow"));
	}
	
	@Override
	protected OrderCondition getGridOrderCondition() {
		return new OrderCondition("a.start_time_", Direction.Desc);
	}

	@Override
	protected SqlObject<Map<String, Object>> getSqlObject() {
		SqlObject<Map<String, Object>> sqlObject = new SqlObject<Map<String, Object>>() {
			// 自己根据条件构建实际的sql
			@Override
			public String getSql(Condition condition) {
				Assert.notNull(condition);
				return getSelect() + " " + getFromWhereSql(condition);
			}

			@Override
			public String getFromWhereSql(Condition condition) {
				Assert.notNull(condition);
				String expression = condition.getExpression();
				if (!expression.startsWith("order by")) {
					expression = "where " + expression;
				}
				return getFrom().replace("${condition}", expression);
			}
		};
		
		// 构建查询语句,where和order by不要包含在sql中(要统一放到condition中)
		StringBuffer selectSql = new StringBuffer();
		selectSql.append("select t.*,getProcessInstanceSubject(t.proc_inst_id_) as subject");

		StringBuffer fromSql = new StringBuffer();
		fromSql.append(" from (select a.id_,c.name_ as procinstname,a.name_ as name,a.start_time_,a.end_time_,a.duration_,a.proc_inst_id_");
		fromSql.append(",a.task_def_key_,b.end_time_ as p_end_time,a.due_date_ as due_date,d.name as receiver");
		fromSql.append(",h.suspension_state_ pstatus,c.key_ procinstkey");
		fromSql.append(",(select id from bc_wf_deploy deploy where deploy.deployment_id=c.deployment_id_) as deploy_id");
		fromSql.append(" from act_hi_taskinst a");
		fromSql.append(" inner join act_hi_procinst b on b.proc_inst_id_=a.proc_inst_id_");
		fromSql.append(" inner join act_re_procdef c on c.id_=a.proc_def_id_");
		fromSql.append(" left join act_ru_execution h on h.proc_inst_id_ = a.proc_inst_id_");
		fromSql.append(" left join bc_identity_actor d on d.code=a.assignee_");
		fromSql.append(" ${condition} ) t");

		sqlObject.setSelect(selectSql.toString());
		sqlObject.setFrom(fromSql.toString());
		// 注入参数
		sqlObject.setArgs(null);

		// 数据映射器
		sqlObject.setRowMapper(new RowMapper<Map<String, Object>>() {
			public Map<String, Object> mapRow(Object[] rs, int rowNum) {
				Map<String, Object> map = new HashMap<String, Object>();
				int i = 0;
				map.put("id", rs[i++]);
				map.put("procinstName", rs[i++]);
				map.put("name", rs[i++]);
				map.put("start_time", rs[i++]);
				map.put("end_time", rs[i++]);
				map.put("duration", rs[i++]);
				map.put("procinstId", rs[i++]);
				map.put("taskDefKey", rs[i++]);
				map.put("p_end_time", rs[i++]);
				map.put("due_date", rs[i++]);
				map.put("receiver", rs[i++]);
				map.put("pstatus", rs[i++]);
				map.put("procinstKey", rs[i++]);
				map.put("deployId", rs[i++]);
				map.put("subject", rs[i++]);
				map.put("accessControlDocType","ProcessInstance");

				// 根据结束时间取得状态
				if (map.get("end_time") != null) {
					// 已完成
					map.put("status", BCConstants.STATUS_DISABLED);
				}else{
					// 未完成
					map.put("status", BCConstants.STATUS_ENABLED);
				}
				
				//判断流程状态
				if (map.get("pstatus") == null) {
					map.put("pstatus", WorkspaceServiceImpl.COMPLETE);
				} else{
					if(map.get("pstatus").toString().equals(String.valueOf(SuspensionState.ACTIVE.getStateCode()))){//处理中
						map.put("pstatus", String.valueOf(SuspensionState.ACTIVE.getStateCode()));
					}else if(map.get("pstatus").toString().equals(String.valueOf(SuspensionState.SUSPENDED.getStateCode()))){//已暂停
						map.put("pstatus", String.valueOf(SuspensionState.SUSPENDED.getStateCode()));
					}
				}
				
				if(map.get("subject")!=null&&!map.get("subject").toString().equals("")){
					map.put("accessControlDocName", map.get("subject").toString());
				}else{
					map.put("accessControlDocName", map.get("procinstName").toString());
				}

				return map;
			}
		});
		return sqlObject;
	}

	@Override
	protected List<Column> getGridColumns() {
		List<Column> columns = new ArrayList<Column>();
		columns.add(new IdColumn4MapKey("a.id_", "id"));

		// 状态
		columns.add(new TextColumn4MapKey("", "status",
				getText("flow.task.status"), 50).setSortable(true)
				.setValueFormater(new EntityStatusFormater(getStatus())));
		// 主题
		columns.add(new TextColumn4MapKey(
				"", "subject",
				getText("flow.task.subject"), 200).setSortable(true)
				.setUseTitleFromLabel(true));
		// 名称
		columns.add(new TextColumn4MapKey("a.name_", "name",
				getText("flow.task.name"), 200).setSortable(true)
				.setUseTitleFromLabel(true));

		
		columns.add(new TextColumn4MapKey("d.name", "receiver",
				getText("flow.task.actor"), 120).setSortable(true)
				.setUseTitleFromLabel(true));
		
		//办理期限
		columns.add(new TextColumn4MapKey("a.due_date_", "due_date",
				getText("done.dueDate"), 130).setSortable(true)
				.setUseTitleFromLabel(true)
				.setValueFormater(new CalendarFormater("yyyy-MM-dd HH:mm")));

		columns.add(new TextColumn4MapKey("a.start_time_", "start_time",
				getText("flow.task.startTime"), 150)
				.setSortable(true)
				.setUseTitleFromLabel(true)
				.setValueFormater(
						new CalendarFormater("yyyy-MM-dd HH:mm:ss")));
		columns.add(new TextColumn4MapKey("a.end_time_", "end_time",
				getText("flow.task.endTime"), 150)
				.setSortable(true)
				.setUseTitleFromLabel(true)
				.setValueFormater(
						new CalendarFormater("yyyy-MM-dd HH:mm:ss")));
		
		columns.add(new TextColumn4MapKey("a.duration_", "frmDuration",
				getText("flow.task.duration"), 80).setSortable(true)
				.setValueFormater(new AbstractFormater<String>() {
					@SuppressWarnings("unchecked")
					@Override
					public String format(Object context, Object value) {
						Object duration_obj=((Map<String, Object>)context).get("duration");
						if(duration_obj==null)return null;
						return DateUtils.getWasteTime(Long.parseLong(duration_obj.toString()));
					}	
				}));
		// 流程
		columns.add(new TextColumn4MapKey("c.name_", "procinstName",
				getText("flow.task.category")).setSortable(true)
				.setUseTitleFromLabel(true));
		//流程状态
		columns.add(new TextColumn4MapKey("", "pstatus",
				getText("flow.task.pstatus"), 80).setSortable(true)
				.setValueFormater(new EntityStatusFormater(getPStatus())));

		columns.add(new TextColumn4MapKey("a.task_def_key_", "taskDefKey",
				"任务key值", 80).setSortable(true)
				.setUseTitleFromLabel(true));
		columns.add(new HiddenColumn4MapKey("procinstId", "procinstId"));
		columns.add(new HiddenColumn4MapKey("procinstName", "procinstName"));
		columns.add(new HiddenColumn4MapKey("procinstKey", "procinstKey"));
		columns.add(new HiddenColumn4MapKey("procinstTaskName", "name"));
		columns.add(new HiddenColumn4MapKey("procinstTaskKey", "taskDefKey"));
		columns.add(new HiddenColumn4MapKey("subject", "subject"));
		columns.add(new HiddenColumn4MapKey("accessControlDocType", "accessControlDocType"));
		columns.add(new HiddenColumn4MapKey("accessControlDocName", "accessControlDocName"));
		columns.add(new HiddenColumn4MapKey("deployId", "deployId"));
		return columns;
	}

	@Override
	protected String getGridRowLabelExpression() {
		return "['name']";
	}

	@Override
	protected String[] getGridSearchFields() {
		return new String[] { 
				"d.name"
				, "a.name_"
				, "c.name_",
				"getProcessInstanceSubject(a.proc_inst_id_)" };
	}

	@Override
	protected String getFormActionName() {
		return "historicTaskInstance";
	}

	@Override
	protected PageOption getHtmlPageOption() {
		return super.getHtmlPageOption().setWidth(800).setMinWidth(400)
				.setHeight(400).setMinHeight(300);
	}

	@Override
	protected Toolbar getHtmlPageToolbar() {
		Toolbar tb = new Toolbar();
		// 查看
		tb.addButton(new ToolbarButton().setIcon("ui-icon-check")
				.setText(getText("label.read"))
				.setClick("bc.historicTaskInstanceSelectView.open"));
		
		tb.addButton(Toolbar.getDefaultToolbarRadioGroup(this.getStatus(),
				"status",2,
				getText("title.click2changeSearchStatus")));
			
		// 搜索按钮
		tb.addButton(this.getDefaultSearchToolbarButton());

		return tb;
	}

	/**
	 * 状态值转换:已完成|未完成|全部
	 * 
	 */
	protected Map<String, String> getStatus() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put(String.valueOf(BCConstants.STATUS_ENABLED),
				getText("flow.task.status.doing"));
		map.put(String.valueOf(BCConstants.STATUS_DISABLED),
				getText("flow.task.status.finished"));
		map.put("", getText("bc.status.all"));
		return map;
	}
	
	/**
	 * 流程状态值转换:流转中|已暂停|已结束|全部
	 * 
	 */
	protected Map<String, String> getPStatus() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put(String.valueOf(SuspensionState.ACTIVE.getStateCode()),
				getText("done.status.processing"));
		map.put(String.valueOf(SuspensionState.SUSPENDED.getStateCode()),
				getText("done.status.suspended"));
		map.put(String.valueOf(WorkspaceServiceImpl.COMPLETE),
				getText("done.status.finished"));
		map.put("", getText("bc.status.all"));
		return map;
	}

	@Override
	protected Condition getGridSpecalCondition() {
		// 状态条件
		AndCondition ac = new AndCondition();
		
		if(status!=null && status.length()>0){
			String[] ss = status.split(",");
			if (ss.length == 1) {
				if (ss[0].equals(String.valueOf(BCConstants.STATUS_ENABLED))) {
					ac.add(new IsNullCondition("a.end_time_"));
				} else if (ss[0].equals(String
						.valueOf(BCConstants.STATUS_DISABLED)))
					ac.add(new IsNotNullCondition("a.end_time_"));
			}
		}
		
		ac.add(new IsNullCondition("h.parent_id_"));

		return ac.isEmpty() ? null : ac;
	}

	@Override
	protected Json getGridExtrasData() {
		Json json = new Json();
		// 状态条件
		if (status != null && status.length() > 0)
			json.put("status", status);
		
		return json;
	}

	@Override
	protected String getGridDblRowMethod() {
		return "bc.historicTaskInstanceSelectView.open";
	}

	@Override
	protected String getHtmlPageJs() {
		return this.getModuleContextPath() + "/historictaskinstance/view.js";
	}

	// ==高级搜索代码开始==
	@Override
	protected boolean useAdvanceSearch() {
		return true;
	}

	public JSONArray processList;
	public JSONArray taskList;

	@Override
	protected void initConditionsFrom() throws Exception {
		List<String> values=this.historicTaskInstanceService.findProcessNames();
		List<Map<String,String>> list = new ArrayList<Map<String,String>>();
		Map<String,String> map;
		for(String value : values){
			map = new HashMap<String, String>();
			map.put("key", value);
			map.put("value", value);
			list.add(map);
		}
		this.processList = OptionItem.toLabelValues(list);
		
		values=this.historicTaskInstanceService.findTaskNames();
		list = new ArrayList<Map<String,String>>();
		for(String value : values){
			map = new HashMap<String, String>();
			map.put("key", value);
			map.put("value", value);
			list.add(map);
		}
		this.taskList = OptionItem.toLabelValues(list);
	}
	// ==高级搜索代码结束==

	
	public String startFlowKey;//流程的key
	public String processData;/* json格式 { 
									procinstId:流程实例id,
									procinstName:流程实例名称,
									procinstKey:流程Key,
									procinstTaskName:任务名称,
									procinstTaskKey:任务key,
									procinstTaskId:任务id  }*/
	
	public String startFlow() throws Exception {
		Json json=new Json();
		String procinstId=this.historicTaskInstanceService.doStartFlow(this.startFlowKey, this.processData);
		json.put("success", true);
		json.put("procinstId", procinstId);
		this.json=json.toString();
		return "json";
	}
	
}
