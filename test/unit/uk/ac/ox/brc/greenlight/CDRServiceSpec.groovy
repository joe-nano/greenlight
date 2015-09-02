package uk.ac.ox.brc.greenlight

import com.mirth.results.client.PatientModel
import com.mirth.results.client.result.ResultModel
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import uk.ac.ox.brc.greenlight.Audit.CDRLog
import uk.ac.ox.ndm.mirth.datamodel.exception.rest.ClientException
import uk.ac.ox.ndm.mirth.datamodel.rest.client.KnownFacility
import uk.ac.ox.ndm.mirth.datamodel.rest.client.KnownOrganisation
import uk.ac.ox.ndm.mirth.datamodel.rest.client.KnownPatientStatus

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(CDRService)
@Mock([ConsentForm,Patient,ConsentFormTemplate,CDRLog])
class CDRServiceSpec extends Specification {



	def setup(){
		service.patientService = Mock(PatientService)
		service.consentFormService = Mock(ConsentFormService)
		service.CDRLogService = Mock(CDRLogService)
	}

	void "connectToCDRAndSendConsentForm sends consent into CDR and returns success"(){
		setup:
		def nhsNumber = "1234567890"
		def hospitalNumber = "123"
		def consentForm = new ConsentForm(template:new ConsentFormTemplate(cdrUniqueId: "GEL") )
		//Mock the internal methods of the Service
		service.metaClass.getCDRClient   = {
			def client = new Object()
			client.metaClass.createOrUpdatePatientConsent = {
				String consent, String patient, KnownFacility receivingFacility, KnownOrganisation organisation, KnownPatientStatus consentStatus,Closure clsr ->
					def result = new ResultModel<PatientModel>()
					result.metaClass.isOperationSucceeded = {
						return  true
					}
					result.metaClass.getConditionDetailsAsString = {
						return  "Detail_Result_of_Actions"
					}
					return result
			}
			return client
		}
		service.metaClass.getCDRFacility = {new Object()}
		service.metaClass.findKnownOrganisation = {return KnownOrganisation.GEL_CSC_V1}
		service.metaClass.findKnownFacility = {return KnownFacility.TEST}
		service.metaClass.grailsApplication.getConfig = { [cdr:[knownFacility:"TEST",organisation:"Greenlight"] ]  }

		when:
		def result = service.connectToCDRAndSendConsentForm(nhsNumber,hospitalNumber,consentForm);

		then:
		result.success
		result.log == "Detail_Result_of_Actions"
	}

	void "connectToCDRAndSendConsentForm returns exception message when has error"(){
		setup:
		def nhsNumber = "1234567890"
		def hospitalNumber = "123"
		def consentForm = new ConsentForm(template:new ConsentFormTemplate(cdrUniqueId: "GEL") )
		//Mock the internal methods of the Service
		service.metaClass.getCDRClient   = {
			def client = new Object()
			client.metaClass.createOrUpdatePatientConsent = {
				String consent, String patient, KnownFacility receivingFacility, KnownOrganisation organisation, KnownPatientStatus consentStatus,Closure clsr ->
					throw new ClientException("Exception in calling CDR")
			}
			return client
		}
		service.metaClass.getCDRFacility = {new Object()}
		service.metaClass.findKnownOrganisation = {return KnownOrganisation.GEL_CSC_V1}
		service.metaClass.findKnownFacility = {return KnownFacility.TEST}
		service.metaClass.grailsApplication.getConfig = { [cdr:[knownFacility:"TEST",organisation:"Greenlight"] ]  }

		when:
		def result = service.connectToCDRAndSendConsentForm(nhsNumber,hospitalNumber,consentForm);

		then:
		!result.success
		result.log == "Exception in calling CDR"
	}

	def "findKnownPatientStatus returns KnownPatientStatus based on ConsentForm.ConsentStatus"(){
		when:
		def actual = service.findKnownPatientStatus(consentStatus)
		assert ConsentForm.ConsentStatus.values().size() == 3
		assert KnownPatientStatus.values().size() == 5

		then:
		actual == expected

		where:
		consentStatus							|	expected
		ConsentForm.ConsentStatus.NON_CONSENT	|	KnownPatientStatus.NON_CONSENT
		ConsentForm.ConsentStatus.FULL_CONSENT	|	KnownPatientStatus.CONSENTED
		ConsentForm.ConsentStatus.CONSENT_WITH_LABELS	|	KnownPatientStatus.RESTRICTED_CONSENT
		'UN-KNOWN'										|	null
	}

	def "findKnownOrganisation will return KnownOrganisation enum value"(){
		when:
		def actual = service.findKnownOrganisation(organisationName)
		assert KnownOrganisation.values().size() == 6

		then:
		actual == expected

		where:
		organisationName|	expected
		"ORB_PRE_V1_2"	|	KnownOrganisation.ORB_PRE_V1_2
		"ORB_GEN_V1"	|	KnownOrganisation.ORB_GEN_V1
		"ORB_CRA_V1"	|	KnownOrganisation.ORB_CRA_V1
		"GEL_CSC_V1"	|	KnownOrganisation.GEL_CSC_V1
		"GEL_CSC_V2"	|	KnownOrganisation.GEL_CSC_V2
		"ORB_GEN_V2"	|	KnownOrganisation.ORB_GEN_V2
		"UNKNOWN"		|	null
	}

	def "findKnownFacility will return KnownFacility enum value"(){
		when:
		def actual = service.findKnownFacility(facilityName)
		assert KnownFacility.values().size() == 2

		then:
		actual == expected

		where:
		facilityName	|	expected
		"TEST"			|	KnownFacility.TEST
		"PRODUCTION"	|	KnownFacility.PRODUCTION
	}

	def "saveOrUpdateConsentForm returns NOP for NEW consent & if NEWER consent already exists"() {
		given:"Newer saved_in_CDR consent exists"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "123").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date()).save(failOnError: true,flush: true)
		def newerConsentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date().plus(1),savedInCDR: true).save(failOnError: true,flush: true)

		when:"Saving a NEW consent which its consentDate is older than that new one"
		1 * service.consentFormService.findConsentsOfSameTypeAfterThisConsentWhichAreSavedInCDR(_,_,_) >> {[newerConsentForm]}
		def result = service.saveOrUpdateConsentForm(patient,consentForm,true)

		then:"It should not pass it to CDR"
		result.success == true
		result.log == "no operation required"
	}

	def "saveOrUpdateConsentForm removes OLDER saved_in_cdr consent from CDR if already exists and passes the new one to CDR"() {
		given:"Older saved_in_CDR consent exists"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "123").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def newerConsentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date()).save(failOnError: true,flush: true)
		def olderConsentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date().minus(1),savedInCDR: true).save(failOnError: true,flush: true)

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert patient.hospitalNumber == hospitalNumber
				assert consent.id == olderConsentForm.id
				assert temp.id == olderConsentForm.template.id
				CDR_Remove_ConsentCalled = true
				return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert patient.hospitalNumber == hospitalNumber
				assert consent.id == newerConsentForm.id
				assert temp.id == newerConsentForm.template.id
				CDR_Send_ConsentCalled = true
				return  [success: true, log: "Sent_Log_TEXT"]
		}

		when:"Saving a NEW consent which its consentDate is newer than that old one"
		1 * service.consentFormService.findConsentsOfSameTypeAfterThisConsentWhichAreSavedInCDR(_,_,_) >> {[]}
		1 * service.consentFormService.findAnyConsentOfSameTypeBeforeThisConsentWhichAreSavedInCDR(_,_,_) >> {olderConsentForm}

		def result = service.saveOrUpdateConsentForm(patient,newerConsentForm,true)

		then:"the old one will be removed from CDR and the new one will be passed to CDR"
		CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		result.success == true
		result.log == "Sent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when patient updated and consent NOT updated and consent was NOT Sent to CDR"(){
		given:"patient updated and consent NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "123").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date()).save(failOnError: true,flush: true)
		patient.hospitalNumber = "UPDATED"

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should not pass it to CDR"
		result.success == true
		result.log == "no operation required"
	}

	def "saveOrUpdateConsentForm when patient updated and consent NOT updated and consent was Sent to CDR and has older consent"(){
		given:"patient updated and consent NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)

		def olderConsentForm  = new ConsentForm(formID: "GEL67890",accessGUID: "456", template:template, patient:patient,consentDate: new Date().minus(1),savedInCDR: false).save(failOnError: true,flush: true)
		def oldestConsentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date().minus(2),savedInCDR: false).save(failOnError: true,flush: true)

		patient.hospitalNumber = "UPDATED"


		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert "OLD" == hospitalNumber // removes the previous old-patient with its consent
				assert consent.id == consentForm.id
				assert temp.id == consentForm.template.id
				CDR_Remove_ConsentCalled = true
				return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert "OLD" == hospitalNumber // add the previous old-patient with its before latest consent
				assert consent.id == olderConsentForm.id
				assert temp.id == olderConsentForm.template.id
				CDR_Send_ConsentCalled = true
				return  [success: true, log: "Sent_Log_TEXT"]
		}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent ->
				assert consent.id == consentForm.id
				assert pat.id == patient.id
				assert pat.hospitalNumber == "UPDATED"
				addNewConsentCalled = true
				return  [success: true, log: "addNewConsent_Log_TEXT"]
		}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should remove the old one and add the latest older before and add the new one"
		1 *  service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {olderConsentForm}
		CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		addNewConsentCalled
		result.success == true
		result.log == "addNewConsent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when patient updated and consent NOT updated and consent was Sent to CDR and does NOT have any older consent"(){
		given:"patient updated and consent NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		patient.hospitalNumber = "UPDATED"


		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert "OLD" == hospitalNumber // removes the previous old-patient with its consent
				assert consent.id == consentForm.id
				assert temp.id == consentForm.template.id
				CDR_Remove_ConsentCalled = true
				return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = { nhsNumber, hospitalNumber, consent, temp -> CDR_Send_ConsentCalled = true }

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent ->
			assert consent.id == consentForm.id
			assert pat.id == patient.id
			assert pat.hospitalNumber == "UPDATED"
			addNewConsentCalled = true
			return  [success: true, log: "addNewConsent_Log_TEXT"]
		}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should remove the old one and add the new one"
		1 *  service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {null}
		CDR_Remove_ConsentCalled
		!CDR_Send_ConsentCalled
		addNewConsentCalled
		result.success == true
		result.log == "addNewConsent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent template is updated and patient NOT updated and old consent was Sent to CDR and old consent has older consent"(){
		given:"consent template updated and patient NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)

		def oldTemplate = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def newTemplate = new ConsentFormTemplate(name:"temp2",namePrefix:"TEMP2",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:oldTemplate, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)

		def olderConsentForm  = new ConsentForm(formID: "GEL67890",accessGUID: "456", template:oldTemplate, patient:patient,consentDate: new Date().minus(1),savedInCDR: false).save(failOnError: true,flush: true)
		def oldestConsentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:oldTemplate, patient:patient,consentDate: new Date().minus(2),savedInCDR: false).save(failOnError: true,flush: true)

		consentForm.template = newTemplate

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert patient.hospitalNumber == hospitalNumber
				assert consent.id == consentForm.id
				assert temp.id == oldTemplate.id // removes the previous old-consent
				CDR_Remove_ConsentCalled = true
				return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = {
			nhsNumber, hospitalNumber, consent, temp ->
				assert patient.nhsNumber 	  == nhsNumber
				assert patient.hospitalNumber == hospitalNumber
				assert consent.id == olderConsentForm.id
				assert temp.id == olderConsentForm.template.id 	// send older consent to CDR
				CDR_Send_ConsentCalled = true
				return  [success: true, log: "Sent_Log_TEXT"]
		}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent ->
			assert consent.id == consentForm.id
			assert consent.template.id == consentForm.template.id
			assert pat.id == patient.id
			assert pat.hospitalNumber == patient.hospitalNumber
			addNewConsentCalled = true
			return  [success: true, log: "addNewConsent_Log_TEXT"]
		}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should remove the old one and add the latest older before and add the new one"
		1 *  service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {olderConsentForm}
		CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		addNewConsentCalled
		result.success == true
		result.log == "addNewConsent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent template is updated and patient NOT updated and old consent was NOT Sent to CDR"(){
		given:"consent template updated and patient NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)

		def oldTemplate = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def newTemplate = new ConsentFormTemplate(name:"temp2",namePrefix:"TEMP2",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:oldTemplate, patient:patient,consentDate: new Date(),savedInCDR: false).save(failOnError: true,flush: true)

		consentForm.template = newTemplate

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {nhsNumber, hospitalNumber, consent, temp -> CDR_Remove_ConsentCalled = false}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = { nhsNumber, hospitalNumber, consent, temp -> CDR_Send_ConsentCalled = false}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent -> addNewConsentCalled = false}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should remove the old one and add the latest older before and add the new one"
		0 *  service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {}
		!CDR_Remove_ConsentCalled
		!CDR_Send_ConsentCalled
		!addNewConsentCalled
		result.success == true
		result.log == "no operation required"
	}

	def "saveOrUpdateConsentForm when consent date is updated and patient NOT updated and old consent was NOT Sent to CDR"(){
		given:"consentDate updated and patient NOT updated"
		def patient  = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: false).save(failOnError: true,flush: true)
		consentForm.consentDate = new Date().minus(1)

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent ->
			assert consent.id == consentForm.id
			assert pat.id == patient.id
			addNewConsentCalled = true
			return  [success: true, log: "addNewConsent_Log_TEXT"]
		}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should treat it as a new added consent"
		addNewConsentCalled
		result.success == true
		result.log == "addNewConsent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent date is updated and patient NOT updated and old consent was Sent to CDR and the new date is after the old date "(){
		given:"consent date updated and patient NOT updated and the new date is after the old date"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL12345",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		consentForm.consentDate = new Date().plus(1)

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {nhsNumber, hospitalNumber, consent, temp -> CDR_Remove_ConsentCalled = false}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = { nhsNumber, hospitalNumber, consent, temp ->
			CDR_Send_ConsentCalled = true
			assert patient.nhsNumber 	  == nhsNumber
			assert patient.hospitalNumber == hospitalNumber
			assert consent.id == consentForm.id
			assert temp.id == consentForm.template.id
			return  [success: true, log: "Sent_Log_TEXT"]
		}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent -> addNewConsentCalled = false}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should just pass it to CDR"
		!CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		!addNewConsentCalled
		result.success == true
		result.log == "Sent_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent date is updated and patient NOT updated and old consent was Sent to CDR and the new date is before the old date "(){
		given:"consent date updated and patient NOT updated and the new date is before the old date"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)


		def latestConsentForm  = new ConsentForm(formID: "GEL78950",accessGUID: "123", template:template, patient:patient,consentDate: new Date().minus(5),savedInCDR: false).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		consentForm.consentDate = new Date().minus(10)


		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = { nhsNumber, hospitalNumber, consent, temp ->
			CDR_Send_ConsentCalled = true
			assert  nhsNumber 	  == patient.nhsNumber
			assert hospitalNumber == patient.hospitalNumber
			assert consent.id == latestConsentForm.id
			assert temp.id    == latestConsentForm.template.id
			return  [success: true, log: "Sent_Log_TEXT"]
		}


		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = {nhsNumber, hospitalNumber, consent, temp -> CDR_Remove_ConsentCalled = false}


		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent -> addNewConsentCalled = false}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should just pass the latestConsentForm to CDR and make the new consent as not sent to CDR"
		1 * service.consentFormService.findLatestConsentOfSameTypeAfterThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {latestConsentForm}

		!CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		!addNewConsentCalled

		//make the new consent as not sent to CDR
		consentForm.savedInCDR == false
		consentForm.passedToCDR == false
		consentForm.savedInCDRStatus == null

		result.success == true
		result.log == "no operation required"
	}

	def "saveOrUpdateConsentForm when consent formStatus was NORMAL and now it is changed to UN-NORMAL and patient NOT updated and old consent was Sent to CDR"(){
		given:"consent formStatus updated from NORMAL to UN-NORMAL and patient NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def consentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.NORMAL, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		consentForm.formStatus = ConsentForm.FormStatus.SPOILED

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = { nhsNumber, hospitalNumber, consent, temp ->
			CDR_Remove_ConsentCalled = true
			assert  nhsNumber 	  == patient.nhsNumber
			assert hospitalNumber == patient.hospitalNumber
			assert consent.id == consentForm.id
			assert temp.id    == consentForm.template.id
			return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_ConsentCalled = {nhsNumber, hospitalNumber, consent, temp -> CDR_Send_ConsentCalled = false}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent -> addNewConsentCalled = false}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should just remove the old one from CDR"
		1 * service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {null}

		CDR_Remove_ConsentCalled
		!CDR_Send_ConsentCalled
		!addNewConsentCalled
		result.success == true
		result.log == "Remove_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent formStatus was NORMAL and now it is changed to UN-NORMAL and patient NOT updated and old consent was Sent to CDR and there is an older not sent consent"(){
		given:"consent formStatus updated from NORMAL to UN-NORMAL and patient NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def olderConsentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.NORMAL, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date().minus(1),savedInCDR: true).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.NORMAL, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		consentForm.formStatus = ConsentForm.FormStatus.SPOILED

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = { nhsNumber, hospitalNumber, consent, temp ->
			CDR_Remove_ConsentCalled = true
			assert  nhsNumber 	  == patient.nhsNumber
			assert hospitalNumber == patient.hospitalNumber
			assert consent.id == consentForm.id
			assert temp.id    == consentForm.template.id
			return  [success: true, log: "Remove_Log_TEXT"]
		}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = {nhsNumber, hospitalNumber, consent, temp ->
			CDR_Send_ConsentCalled = true
			assert nhsNumber 	  == patient.nhsNumber
			assert hospitalNumber == patient.hospitalNumber
			assert consent.id == olderConsentForm.id
			assert temp.id    == olderConsentForm.template.id
			return  [success: true, log: "Send_Log_TEXT"]
		}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent -> addNewConsentCalled = false}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should just remove the old one from CDR and add the older consent"
		1 * service.consentFormService.findLatestConsentOfSameTypeBeforeThisConsentWhichAreNotSavedInCDR(_,_,_,_) >> {olderConsentForm}

		CDR_Remove_ConsentCalled
		CDR_Send_ConsentCalled
		!addNewConsentCalled
		result.success == true
		result.log == "Remove_Log_TEXT"
	}

	def "saveOrUpdateConsentForm when consent formStatus was UN-NORMAL and now it is changed to NORMAL and patient NOT updated"(){
		given:"consent formStatus updated from NORMAL to UN-NORMAL and patient NOT updated"
		def patient = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)

		def consentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.SPOILED, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)
		consentForm.formStatus = ConsentForm.FormStatus.NORMAL

		def CDR_Remove_ConsentCalled = false
		service.metaClass.CDR_Remove_Consent = { nhsNumber, hospitalNumber, consent, temp -> CDR_Remove_ConsentCalled = true}

		def CDR_Send_ConsentCalled = false
		service.metaClass.CDR_Send_Consent = {nhsNumber, hospitalNumber, consent, temp -> CDR_Send_ConsentCalled = true}

		def addNewConsentCalled = false
		service.metaClass.addNewConsent = {Patient pat,ConsentForm consent ->
			addNewConsentCalled = true
			return [success:true,log:"addNewConsent internal log"]
		}

		when:"Passing them to saveOrUpdateConsentForm"
		def result = service.saveOrUpdateConsentForm(patient,consentForm,false)

		then:"It should just remove the old one from CDR and add the older consent"
		!CDR_Remove_ConsentCalled
		!CDR_Send_ConsentCalled
		addNewConsentCalled
		result.success == true
		result.log == "addNewConsent internal log"
	}

	def "CDR_Remove_Consent  prepares a consent for removal from CDR and updates its status"(){
		given:"patient and consent are ready to be removed from CDR"
		def patient  = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.SPOILED, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)

		def connectToCDRAndRemoveConsentFrom_Called = false
		service.metaClass.connectToCDRAndRemoveConsentFrom = { nhsNumber, hospitalNumber, temp ->
			connectToCDRAndRemoveConsentFrom_Called = true
			return [success: true,log:"Removed_LOG_TEXT"]
		}

		when:"CDR_Remove_Consent is called"
		def result = service.CDR_Remove_Consent(patient.nhsNumber,patient.hospitalNumber,consentForm,template)

		then: "connectToCDRAndRemoveConsentFrom is called and also the status of the consent is updated"
		1 * service.CDRLogService.add(consentForm.id,template.id,patient.nhsNumber,patient.hospitalNumber,true,"Removed_LOG_TEXT","remove") >> {}
		connectToCDRAndRemoveConsentFrom_Called
		consentForm.savedInCDR  == false
		consentForm.passedToCDR == false
		consentForm.savedInCDRStatus   == null
		consentForm.dateTimeSavedInCDR == null
		result.success
		result.log == "Removed_LOG_TEXT"
	}

	def "CDR_Send_Consent  prepares a consent for adding to CDR and updates its status"(){
		given:"patient and consent are ready to be saved in CDR"
		def patient  = new Patient(nhsNumber: "1234567890",hospitalNumber: "OLD").save(failOnError: true,flush: true)
		def template = new ConsentFormTemplate(name:"temp1",namePrefix:"TEMP",templateVersion: "V1" ).save(failOnError: true,flush: true)
		def consentForm = new ConsentForm(formStatus: ConsentForm.FormStatus.SPOILED, formID: "GEL56890",accessGUID: "123", template:template, patient:patient,consentDate: new Date(),savedInCDR: true).save(failOnError: true,flush: true)

		def connectToCDRAndSendConsentForm_Called = false
		service.metaClass.connectToCDRAndSendConsentForm = { nhsNumber, hospitalNumber, cons ->
			connectToCDRAndSendConsentForm_Called = true
			return [success: false,log:"NOT_SAVE_IN_CDR_TEST_LOG"]
		}

		when:"CDR_Send_Consent is called"
		def result = service.CDR_Send_Consent(patient.nhsNumber,patient.hospitalNumber,consentForm,template)

		then: "connectToCDRAndSendConsentForm is called and also the status of the consent is updated"
		1 * service.CDRLogService.add(consentForm.id,template.id,patient.nhsNumber,patient.hospitalNumber,false,"NOT_SAVE_IN_CDR_TEST_LOG","save") >> {}
		connectToCDRAndSendConsentForm_Called
		consentForm.savedInCDR  == false
		consentForm.passedToCDR == true
		consentForm.savedInCDRStatus == "NOT_SAVE_IN_CDR_TEST_LOG"
		consentForm.dateTimeSavedInCDR
		!result.success
		result.log == "NOT_SAVE_IN_CDR_TEST_LOG"
	}
}