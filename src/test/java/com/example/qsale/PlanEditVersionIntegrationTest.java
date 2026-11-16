package com.example.qsale;

import com.example.qsale.notification.domain.EmailService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanEditVersionIntegrationTest extends AbstractContainerBaseTest {
    @Autowired MockMvc mvc;
    @MockitoBean EmailService email;

    @Test void staleEditorsCannotOverwriteANewerBudgetAndFreshEditorsCanContinue() throws Exception {
        String token = token();
        String created = mvc.perform(post("/api/v1/plans").header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(input("Original",10,null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=((Number)JsonPath.read(created,"$.id")).longValue();
        long version=((Number)JsonPath.read(created,"$.version")).longValue();
        String updated= mvc.perform(put("/api/v1/plans/{id}",id).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(input("Original",50,version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long current=((Number)JsonPath.read(updated,"$.version")).longValue();
        assertTrue(current>version);
        mvc.perform(put("/api/v1/plans/{id}",id).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(input("Stale draft",10,version)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/plans/{id}",id).header("Authorization",token))
                .andExpect(jsonPath("$.budget").value(50)).andExpect(jsonPath("$.name").value("Original"));
        mvc.perform(put("/api/v1/plans/{id}",id).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(input("Reviewed draft",50,current)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Reviewed draft"));
    }

    @Test void anUpdateWithoutAReadVersionRequiresTheClientToReload() throws Exception {
        String token=token();
        String created=mvc.perform(post("/api/v1/plans").header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(input("Original",10,null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/v1/plans/{id}",(Number)JsonPath.read(created,"$.id"))
                .header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(input("Without version",20,null))).andExpect(status().isConflict());
    }

    private String token() throws Exception {
        String response=mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Version Test\",\"email\":\"%s@qsale.test\",\"password\":\"VersionTest2026\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer "+JsonPath.read(response,"$.token");
    }

    private String input(String name,int budget,Long version) {
        return "{\"name\":\"%s\",\"type\":\"OTHER\",\"minParticipants\":2,\"budget\":%d%s}"
                .formatted(name,budget,version==null ? "" : ",\"version\":"+version);
    }
}
