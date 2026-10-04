package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiExceptionHandlerTest.ProbeController.class)
@Import(ApiExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountRepository accountRepository;

    @Test
    void unexpectedExceptionReturnsGenericInternalError() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("chi tiết nội bộ"))));
    }

    @Test
    void frameworkErrorKeepsStatusAndReturnsMessage() throws Exception {
        mockMvc.perform(post("/probe/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Phương thức không được hỗ trợ"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("chi tiết nội bộ không được lộ");
        }
    }
}
