package com.syt.activitidemo;

import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest()
public class ActivitiDemoApplicationTests {
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private TaskService taskServicService;

    /**
     * 部署流程，开启了springboot自动部署，测试时无需调用
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void deploy() {
        repositoryService.createDeployment()
                .name("leave")
                .addClasspathResource("processes/leave.bpmn")
                .deploy();
    }

    /**
     * 创建用户和用户组,只需调用一次
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void addUserAndGroup() {
        //创建经理和经理组数据
        User manager1 = identityService.newUser("manager1");
        User manager2 = identityService.newUser("manager2");
        User manager3 = identityService.newUser("manager3");
        Group manager = identityService.newGroup("manager");
        identityService.saveUser(manager1);
        identityService.saveUser(manager2);
        identityService.saveUser(manager3);
        identityService.saveGroup(manager);
        identityService.createMembership(manager1.getId(), manager.getId());
        identityService.createMembership(manager2.getId(), manager.getId());
        identityService.createMembership(manager3.getId(), manager.getId());

        //创建老板组和老板组数据
        User boss1 = identityService.newUser("boss1");
        User boss2 = identityService.newUser("boss2");
        User boss3 = identityService.newUser("boss3");
        Group boss = identityService.newGroup("boss");
        identityService.saveUser(boss1);
        identityService.saveUser(boss2);
        identityService.saveUser(boss3);
        identityService.saveGroup(boss);
        identityService.createMembership(boss1.getId(), boss.getId());
        identityService.createMembership(boss2.getId(), boss.getId());
        identityService.createMembership(boss3.getId(), boss.getId());

        //创建雇员
        User employee = identityService.newUser("employee");
        identityService.saveUser(employee);


    }

    /**
     * 删除流程
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void deleteProcess() {
        runtimeService.deleteProcessInstance("65001", "无");
    }

    /**
     * 员工启动请假流程
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void startProcess() {
        //设置启动用户id
        identityService.setAuthenticatedUserId("employee");
        //启动请假流程
        Map<String, Object> variables = new HashMap<>(16);
        //设置用户节点代理人
        variables.put("employee", "employee");
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("leave", variables);
        System.out.println("流程启动成功，流程id:" + processInstance.getId());
    }


    /**
     * 员工填写请假天数
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void employeeTask1() {
        //员工查询自己的任务
        List<Task> employeeTasks = taskServicService.createTaskQuery().taskCandidateOrAssigned("employee").list();
        employeeTasks.forEach(employeeTask -> {
            System.out.println(employeeTask.getName() + "----" + employeeTask.getId());
            Map<String, Object> variables = taskServicService.getVariables(employeeTask.getId());
            //员工填写请假天数3天
            variables.put("day", 3);
            System.out.println("填写请假天数3天");
            //请假天数3天，由总经理审批
            taskServicService.complete(employeeTask.getId(), variables);
        });

    }

    /**
     * 总经理审批
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void bossExamine() {
        //查询总经理组当前需要处理的任务
        List<Task> bossTasks = taskServicService.createTaskQuery().taskCandidateGroup("boss").list();
        bossTasks.forEach(bossTask -> {
            System.out.println(bossTask.getId() + "-----" + bossTask.getName());
            //总经理1认领任务,认领之后执行上面的查询boss组就不会显示已经认领的任务
            taskServicService.claim(bossTask.getId(), "boss1");
        });

        //总经理boss1查询当前需要处理的任务
        List<Task> boss1Tasks = taskServicService.createTaskQuery().taskCandidateOrAssigned("boss1").list();
        boss1Tasks.forEach(boss1Task -> {
            System.out.println("boss1认领的任务:" + boss1Task.getId() + "-----" + boss1Task.getName());
            //总经理boss1获取员工请假信息
            Map<String, Object> variables = taskServicService.getVariables(boss1Task.getId());
            System.out.println("请假天数" + "-----" + variables.get("day").toString());
            //审核不通过
            variables.put("flag", "false");
            System.out.println("审批不通过");
            taskServicService.complete(boss1Task.getId(), variables);
        });

    }

    /**
     * 总经理审批未通过，员工重新填写请假天数
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void employeeTask2() {
        //员工查询自己的任务
        List<Task> employeeTasks = taskServicService.createTaskQuery().taskCandidateOrAssigned("employee").list();
        employeeTasks.forEach(employeeTask -> {
            //审核未通过，重新请假
            System.out.println(employeeTask.getId() + "-----" + employeeTask.getName());
            Map<String, Object> variables = taskServicService.getVariables(employeeTask.getId());
            //请假天数改为1天，由经理审批
            variables.put("day", 1);
            System.out.println("请假天数更改为1天");
            taskServicService.complete(employeeTask.getId(), variables);
        });

    }

    /**
     * 经理审批
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void managerTask() {
        //经理组查看待办任务
        List<Task> managerTasks = taskServicService.createTaskQuery().taskCandidateGroup("manager").list();
        managerTasks.forEach(managerTask -> {
            System.out.println(managerTask.getId() + "-----" + managerTask.getName());
            //经理2认领任务
            taskServicService.claim(managerTask.getId(), "manager2");
        });
        //经理2查看已经认领任务
        List<Task> manager2Tasks = taskServicService.createTaskQuery().taskCandidateOrAssigned("manager2").list();
        manager2Tasks.forEach(manager2Task -> {
            System.out.println(manager2Task.getId() + "-----" + manager2Task.getName());
            //经理2查看请假天数
            Map<String, Object> variables = taskServicService.getVariables(manager2Task.getId());
            System.out.println("请假天数" + "-------" + variables.get("day").toString());
            //请假通过
            variables.put("flag", "true");
            System.out.println("审批通过");
            taskServicService.complete(manager2Task.getId(), variables);
        });

    }

    /**
     * 经理审批通过，员工查看请假结果
     *
     * @author syt
     * @date 2019/6/28/0028
     */
    @Test
    public void leaveResult() {
        //员工查询已完成流程列表
        List<HistoricProcessInstance> employeeProcesses = historyService.createHistoricProcessInstanceQuery()
                .startedBy("employee")
                .finished()
                .list();
        employeeProcesses.forEach(employeeProcess -> {
            System.out.println("任务ID:" + employeeProcess.getId());
            System.out.println("开始时间：" + employeeProcess.getStartTime());
            System.out.println("流程名称：" + employeeProcess.getProcessDefinitionKey());
            System.out.println("结束时间：" + employeeProcess.getEndTime());
            System.out.println("===========================");
            List<HistoricActivityInstance> HistoricActivityInstances = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(employeeProcess.getId())
                    .finished()
                    .list();
            HistoricActivityInstances.forEach(HistoricActivityInstance -> {
                System.out.println("任务ID:" + HistoricActivityInstance.getId());
                System.out.println("流程实例ID:" + HistoricActivityInstance.getProcessInstanceId());
                System.out.println("活动名称：" + HistoricActivityInstance.getActivityName());
                System.out.println("办理人：" + HistoricActivityInstance.getAssignee());
                System.out.println("开始时间：" + HistoricActivityInstance.getStartTime());
                System.out.println("结束时间：" + HistoricActivityInstance.getEndTime());
                System.out.println("----------------------");
            });
        });


    }


}
