package cn.bc.workflow.deploy.dao.hibernate.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import cn.bc.BCConstants;
import cn.bc.core.query.condition.Condition;
import cn.bc.core.query.condition.impl.AndCondition;
import cn.bc.core.query.condition.impl.EqualsCondition;
import cn.bc.core.query.condition.impl.NotEqualsCondition;
import cn.bc.db.jdbc.RowMapper;
import cn.bc.orm.hibernate.jpa.HibernateCrudJpaDao;
import cn.bc.orm.hibernate.jpa.HibernateJpaNativeQuery;
import cn.bc.workflow.deploy.dao.DeployDao;
import cn.bc.workflow.deploy.domain.Deploy;

/**
 * DAO接口的实现
 * 
 * @author wis
 * 
 */
public class DeployDaoImpl extends HibernateCrudJpaDao<Deploy> implements
		DeployDao {

	private JdbcTemplate jdbcTemplate;

	@Autowired
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	
	public Deploy loadByCode(String code) {
		if (code == null)
			return null;
		int i = code.indexOf(":");
		String version = null;
		if (i != -1) {
			version = code.substring(i + 1);
			code = code.substring(0, i);
		}
		AndCondition c = new AndCondition();
		c.add(new EqualsCondition("code", code));
		if (version != null) {
			c.add(new EqualsCondition("version", version));// 获取指定版本
		} else {
			c.add(new EqualsCondition("status", BCConstants.STATUS_ENABLED));// 获取最新版本
		}
		return this.createQuery().condition(c).singleResult();
	}

	public boolean isUniqueCodeAndVersion(Long currentId, String code,
			String version) {
		Condition c;
		if (currentId == null) {
			c = new AndCondition().add(new EqualsCondition("code", code)).add(
					new EqualsCondition("version", version));

		} else {
			c = new AndCondition().add(new EqualsCondition("code", code))
					.add(new NotEqualsCondition("id", currentId))
					.add(new EqualsCondition("version", version));
		}
		return this.createQuery().condition(c).count() > 0;
	}
	
	
	public Deploy loadByCodeAndId(String code,Long currentId){
		if(code == null )
			return null;
		AndCondition c = new AndCondition();
		c.add(new EqualsCondition("code", code));
		//状态正常
		c.add(new EqualsCondition("status", BCConstants.STATUS_ENABLED));
		
		if(currentId != null){
			//id不等于本对象
			c.add(new NotEqualsCondition("id",currentId));
		}
		return this.createQuery().condition(c).singleResult();
	}
	
	//模板分类
	public List<Map<String, String>> findCategoryOption() {
		String hql="SELECT d.category,1";
		   hql+=" FROM bc_wf_deploy d";
		   hql+=" GROUP BY d.category";
		 return	HibernateJpaNativeQuery.executeNativeSql(getJpaTemplate(), hql,null
		 	,new RowMapper<Map<String, String>>() {
				public Map<String, String> mapRow(Object[] rs, int rowNum) {
					Map<String, String> oi = new HashMap<String, String>();
					int i = 0;
					oi.put("value", rs[i++].toString());
					return oi;
				}
		});
	}

	/**
	 * 通过流程部署id判断此信息是否发布
	 * @param excludeId
	 * @return
	 */
	public Long isReleased(Long excludeId) {
		Long id = null;
		String sql = "select d.id from bc_wf_deploy d where d.id='"+excludeId+"'" +
				"and d.status_="+Deploy.STATUS_RELEASED;
		try {
			id = this.jdbcTemplate.queryForLong(sql);
		} catch (EmptyResultDataAccessException e) {
			e.getStackTrace();
		}
		return id;
	}
}
