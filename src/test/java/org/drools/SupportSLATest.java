/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.acme.SupportRequest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.SignalEvent;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieRuntimeFactory;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.compiler.RuntimeTypeCheckOption;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupportSLATest {
    static final Logger LOG = LoggerFactory.getLogger(SupportSLATest.class);

    private static ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    static KieContainer kContainer;
    static DMNRuntime dmnRuntime;

    @BeforeClass
    public static void init() {
        KieServices kieServices = KieServices.Factory.get();
        kContainer = kieServices.getKieClasspathContainer();
        Results verifyResults = kContainer.verify();
        for (Message m : verifyResults.getMessages()) {
            LOG.info("{}", m);
        }
        dmnRuntime = KieRuntimeFactory.of(kContainer.getKieBase()).get(DMNRuntime.class);
        ((DMNRuntimeImpl) dmnRuntime).setOption(new RuntimeTypeCheckOption(true));
    }

    @Test
    public void testClassic() throws Exception {
        DMNModel model = dmnRuntime.getModel("ns0", "classic");
        DMNContext context = dmnRuntime.newContext();
        context.set("Support Request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", false));

        String in = asJSON(context);
        LOG.info("\nINPUT:\n{}", in);

        DMNResult evaluateAll = dmnRuntime.evaluateAll(model, context);
        LOG.debug("{}", evaluateAll);
        assertThat(evaluateAll.getDecisionResultByName("Determine Priority").getResult()).isEqualTo("Medium");

        String out = asJSON(evaluateAll);
        LOG.info("\nOUTPUT:\n{}", out);
    }

    @Test
    public void testUsingFP() throws Exception {
        ((DMNRuntimeImpl) dmnRuntime).setOption(new RuntimeTypeCheckOption(true));
        DMNModel model = dmnRuntime.getModel("ns1", "usingFP");
        DMNContext context = dmnRuntime.newContext();
        context.set("Support Request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", false));

        String in = asJSON(context);
        LOG.info("\nINPUT:\n{}", in);

        DMNResult evaluateAll = dmnRuntime.evaluateAll(model, context);
        LOG.debug("{}", evaluateAll);
        assertThat(evaluateAll.getDecisionResultByName("Determine Priority").getResult()).isEqualTo("Medium");
        assertThat(evaluateAll.getDecisionResultByName("Processed Request").getResult()).hasFieldOrPropertyWithValue("priority", "Medium");

        String out = asJSON(evaluateAll);
        LOG.info("\nOUTPUT:\n{}", out);
    }

    @Test
    public void testProcessClassicHigh() {
        KieSession ksession = kContainer.getKieBase().newKieSession();
        MyProcessListener pListener = new MyProcessListener();
        ksession.addEventListener(pListener);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("classic", Map.of("request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", true)));

        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getVariable("request")).hasFieldOrPropertyWithValue("priority", "High");
        assertThat(pListener.getNodesTriggered()).contains("Pager");
        assertThat(pListener.getSignals()).contains("signal1");
    }

    @Test
    public void testProcessClassicMedium() {
        KieSession ksession = kContainer.getKieBase().newKieSession();
        MyProcessListener pListener = new MyProcessListener();
        ksession.addEventListener(pListener);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("classic", Map.of("request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", false)));

        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getVariable("request")).hasFieldOrPropertyWithValue("priority", "Medium");
        assertThat(pListener.getNodesTriggered()).doesNotContain("Pager");
        assertThat(pListener.getSignals()).doesNotContain("signal1");
    }

    @Test
    public void testProcessUsingFPHigh() {
        KieSession ksession = kContainer.getKieBase().newKieSession();
        MyProcessListener pListener = new MyProcessListener();
        ksession.addEventListener(pListener);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("usingFP", Map.of("request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", true)));

        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getVariable("request")).hasFieldOrPropertyWithValue("priority", "High");
        assertThat(pListener.getNodesTriggered()).contains("Pager");
        assertThat(pListener.getSignals()).contains("signal1");
    }

    @Test
    public void testProcessUsingFPMedium() {
        KieSession ksession = kContainer.getKieBase().newKieSession();
        MyProcessListener pListener = new MyProcessListener();
        ksession.addEventListener(pListener);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("usingFP", Map.of("request", new SupportRequest("John Doe", "47", "info@redhat.com", "+1", "somewhere", false)));

        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getVariable("request")).hasFieldOrPropertyWithValue("priority", "Medium");
        assertThat(pListener.getNodesTriggered()).doesNotContain("Pager");
        assertThat(pListener.getSignals()).doesNotContain("signal1");
    }

    private String asJSON(DMNContext context) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(context.getAll());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String asJSON(DMNResult evaluateAll) {
        return asJSON(evaluateAll.getContext());
    }

}

class MyProcessListener extends DefaultProcessEventListener {

    private List<String> nodesTriggered = new ArrayList<>();
    private List<String> signals = new ArrayList<>();

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        nodesTriggered.add(event.getNodeInstance().getNodeName());
    }

    @Override
    public void onSignal(SignalEvent event) {
        signals.add(event.getSignalName());
    }

    public List<String> getNodesTriggered() {
        return Collections.unmodifiableList(nodesTriggered);
    }

    public List<String> getSignals() {
        return Collections.unmodifiableList(signals);
    }
    
}