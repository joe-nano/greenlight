package uk.ac.ox.brc.greenlight

class ConsentFormController {

	def consentEvaluationService
    def consentFormService
	def patientService


    def find()
    {
        def result= consentFormService.search(params);
        render view:"search", model:[consentForms:result]
    }

	/**
	 * Check the consent status for an NHS or hospital number.
	 * @return
	 */
	def checkConsent(){
		String lookupId = params.searchInput
		def model = [searchInput: lookupId]
		// If we don't have an ID to lookup, respond with an error
		if(!lookupId){
			model.errormsg = "A lookup ID must be provided for 'lookupId'"
		}else{
			// Attempt to find the patient
			def patient = patientService.findByNHSOrHospitalNumber(lookupId)
			if(!patient){
				model.errormsg = "The lookup ID "+ lookupId +" could not be found"
			}else{
				// Patient exists, let's get the consents
				def consents = consentFormService.getLatestConsentForms(patient)
				model.consents = []
				consents.each{ consentForm ->
					model.consents.push([
							form: [
									name: consentForm.template.name,
									version: consentForm.template.version,
							],
							lastCompleted: consentForm.consentDate,
							consentStatus: consentEvaluationService.getConsentStatus(consentForm).name()
					])
				}
			}
		}
		render view:"cuttingRoom", model: model
	}

	def export (){
        def csvString = consentFormService.exportToCSV()
        def fileName ="consentForms-"+(new Date()).format("dd-MM-yyyy")
        response.setHeader("Content-disposition", "attachment; filename=${fileName}.csv");
		render(contentType: "text/csv;charset=utf-8", text: csvString.toString());
    }
}
