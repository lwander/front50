/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.validator.HasEmailValidator
import com.netflix.spinnaker.front50.validator.HasNameValidator
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ApplicationsControllerSpec extends Specification {

  @Shared
  MockMvc mockMvc

  @Shared
  ApplicationsController controller

  @Shared
  ApplicationDAO dao

  void setup() {
    dao = Mock(ApplicationDAO)
    this.controller = new ApplicationsController(
        applicationDAO: dao,
        applicationValidators: [new HasNameValidator(), new HasEmailValidator()],
        messageSource: new StaticMessageSource()
    )
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  }

  void 'a put should update an application'() {
    setup:
    def sampleApp = new Application("SAMPLEAPP", null, "web@netflix.com", "Andy McEntee",
      null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)

    when:
    def response = mockMvc.perform(put("/default/applications").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApp))
    1 * dao.findByName(_) >> sampleApp
    1 * dao.update("SAMPLEAPP", ["name": "SAMPLEAPP", "email": "web@netflix.com", "owner": "Andy McEntee"])
  }

  void 'a put should not update an application if no name is provided'() {
    setup:
    def sampleApp = new Application(null, null, "web@netflix.com", "Andy McEntee",
      null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)

    when:
    def response = mockMvc.perform(put("/default/applications").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().is4xxClientError()
  }

  void 'a post w/o a name will throw an error'() {
    setup:
    def sampleApp = new Application(null, "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, null, null, null, null, null,
      null, null, null)

    when:
    def response = mockMvc.perform(post("/default/applications/name/app").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    response.andExpect status().is4xxClientError()
  }

  void 'a post w/an existing application will throw an error'() {
    setup:
    def sampleApp = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
        "netflix.com application", "Standalone Application", null, null, null, null, null, null, null, null, null, null,
        null, null, null)

    when:
    def response = mockMvc.perform(post("/default/applications/name/SAMPLEAPP").
        contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    1 * dao.findByName("SAMPLEAPP") >> sampleApp
    response.andExpect status().is4xxClientError()
  }

  void 'a post w/a new application should yield a success'() {
    setup:
    def sampleApp = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, null, null, null, null, null,
      null, null, null)
    dao.create(_, _) >> sampleApp

    when:
    def response = mockMvc.perform(post("/default/applications/name/SAMPLEAPP").
      contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(sampleApp)))

    then:
    1 * dao.findByName(_) >> { throw new NotFoundException() }
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApp))
  }

  void 'a get w/a name should return a JSON document for the found app'() {
    setup:
    def sampleApp = new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, "1265752693581l",
      "1265752693581l", null, null, null, null, null, null)

    when:
    def response = mockMvc.perform(get("/default/applications/name/SAMPLEAPP"))

    then:
    1 * dao.findByName(_) >> sampleApp
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApp))
  }

  void 'a get w/a invalid name should return 404'() {
    when:
    def response = mockMvc.perform(get("/default/applications/name/blah"))

    then:
    1 * dao.findByName(_) >> { throw new NotFoundException("not found!") }
    response.andExpect status().is(404)
  }

  void 'delete should remove an app'() {
    when:
    def response = mockMvc.perform(delete("/default/applications/name/SAMPLEAPP"))

    then:
    1 * dao.findByName("SAMPLEAPP") >> new Application(name: "SAMPLEAPP")
    1 * dao.delete("SAMPLEAPP")
    response.andExpect status().isAccepted()

  }

  void 'index should return a list of applications'() {
    setup:
    def sampleApps = [new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, "1265752693581l",
      "1265752693581l", null, null, null, null, null, null),
                      new Application("SAMPLEAPP-2", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, "1265752693581l",
      "1265752693581l", null, null, null, null, null, null)]

    when:
    def response = mockMvc.perform(get("/${account}/applications"))

    then:
    1 * dao.all() >> sampleApps
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApps))

    where:
    account = "default"
  }

  void "search hits the dao"() {
    setup:
    def sampleApps = [new Application("SAMPLEAPP", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, "1265752693581l",
      "1265752693581l", null, null, null, null, null, null),
                      new Application("SAMPLEAPP-2", "Standalone App", "web@netflix.com", "Kevin McEntee",
      "netflix.com application", "Standalone Application", null, null, null, null, null, "1265752693581l",
      "1265752693581l", null, null, null, null, null, null)]

    when:
    def response = mockMvc.perform(get("/${account}/applications/search?q=p"))

    then:
    1 * dao.search([q:"p"]) >> sampleApps
    response.andExpect status().isOk()
    response.andExpect content().string(new ObjectMapper().writeValueAsString(sampleApps))

    where:
    account = "default"
  }

}
