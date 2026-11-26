# UNISA catalogue import status

Verified against UNISA's official 2027 qualification listings on 26 November 2026.

## Live Supabase catalogue

The live Student Module Hub database now contains **339 qualification records** linked to UNISA's eight qualification-awarding colleges. This includes the initial undergraduate batch and a second verified batch of honours degrees and postgraduate diplomas.

| College | Qualification records |
|---|---:|
| College of Accounting Sciences | 38 |
| College of Agriculture and Environmental Sciences | 55 |
| College of Economic and Management Sciences | 68 |
| College of Education | 81 |
| College of Human Sciences | 15 |
| College of Law | 52 |
| College of Science, Engineering and Technology | 27 |
| Graduate School of Business Leadership | 3 |
| **Total** | **339** |

The second import added **91** college-confirmed honours and postgraduate diploma records. A third import added **122** master's and doctoral records using UNISA's official college-filtered listings and the 2027 exceptions page. All batches use duplicate checks by college and qualification code. The database import is idempotent by college and qualification code; rerunning the same batch does not create duplicates.

Four module records are loaded for **Bachelor of Arts Honours in Corrections Management (98655)** from the 2026 registration curriculum:

- HMPEN80 — Applied Research Methodology in Corrections Management
- HRPEN81 — Research Report in Corrections Management (co-requisite: HMPEN80)
- PEN4801 — Fundamental Penology
- PEN4804 — Strategic Corrections Management

The 2026 curriculum states that HMPEN80 replaces PEN4802 for students who have not already completed PEN4802. Students who completed PEN4802 retain that credit and must register HRPEN81.


## Verified 2026 module curriculum imports

A separate, repeatable SQL seed is committed at `backend/seeds/unisa_verified_module_curricula_2026.sql`. It skips existing rows by qualification and module code.

- **Diploma in Policing (98220): 30 modules** imported from the 2026 qualification curriculum. The curriculum groups were preserved by NQF level (5, 6 and 7). Credits are recorded as 12 per module. The semester field is deliberately left blank because the qualification curriculum page does not identify each module's exact semester number. Selected prerequisite/recommendation notes are stored in the module description.
- **Advanced Diploma in Information Resource Management (90007): 10 modules** imported. The missing qualification record was added to the CSET catalogue. UNISA's subject catalogue identifies these modules as year modules, NQF 7 and 12 credits; semester/period is recorded as 0.
- **BA Honours in Corrections Management (98655): 4 modules** retained and enriched with the verified 2026 credits, NQF 8 and year/semester classification. HMPEN80 is a 12-credit semester module, with its exact semester number left unspecified; HRPEN81 is 36 credits and year-based; PEN4801 and PEN4804 are 24 credits and year-based.

There are now **44 curriculum-linked module records** across these three qualifications, plus one earlier module record associated with the pre-existing test data. This remains a limited curriculum sample, not a complete module catalogue. Continue importing only when the exact qualification curriculum and, where needed, the official module subject entry can be checked.

Sources for the module batch:
- Diploma in Policing 2026 curriculum: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Diploma-in-Policing-%2898220%29
- Advanced Diploma in Information Resource Management 2026 curriculum: https://unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Advanced-Diploma-in-Information-Resource-Management-%2890007%29
- Information Resource Management subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/INFORMATION-RESOURCE-MANAGEMENT
- Policing subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/POLICING
- Corrections Management subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/CORRECTIONS-MANAGEMENT

## Completeness and accuracy boundary

This is a larger verified catalogue foundation, **not the complete UNISA qualification and module catalogue**. The imported 2027 qualification lists are admission listings, not a full curriculum database. Some college sections and postgraduate categories still need additional verification, and many module curricula have not yet been imported.

Do not manufacture module codes, prerequisites, semester designations, credit values or curriculum combinations. Add modules only after checking the correct qualification stream and academic year on the official UNISA registration page. Qualification streams with different module combinations must be represented separately.

The old Bachelor of Education (Senior and Further Education and Training Phases), code 02615, was phased out after 2024 and must not be presented as a current new-intake qualification. UNISA identifies the newer BEd Senior Phase and FET Teaching (90104) as a stream-based qualification; its module combinations differ by stream. The 90104 qualification is present in the database, but no stream-specific module combination has been invented.

The 2027 BA Honours in Corrections Management page says updated registration/curriculum information will be available closer to the registration phase. The 98655 module rows currently represent the verified 2026 curriculum only.

## Official sources

- Undergraduate qualification listings: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Undergraduate-qualifications/Qualifications/All-qualifications
- Honours and postgraduate diploma all-colleges listing: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications
- Accounting Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=ACCOUNTING+SCIENCES
- Agriculture and Environmental Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=AGRICULTURE+AND+ENVIRONMENTAL+SCIENCES
- Economic and Management Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=ECONOMIC+AND+MANAGEMENT+SCIENCES
- Education honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=EDUCATION
- Human Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=HUMAN+SCIENCES
- Law honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=LAW
- Science, Engineering and Technology honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=SCIENCE%2C+ENGINEERING+AND+TECHNOLOGY
- Master's and doctoral qualifications: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Qualifications/All-qualifications
- Master's and doctoral 2027 exceptions (college-specific): https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Masters-doctoral-qualifications-exceptions
- Master's and doctoral qualification exceptions: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Masters-doctoral-qualifications-exceptions
- Undergraduate registration finder: https://www.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules
- UNISA 2026 student rules: https://www.unisa.ac.za/static/myunisa/Content/Learner%20support%20%26%20regions/Documents/Unisa%20Student%20Rules_2026.pdf
- BA Honours Corrections Management (2026 curriculum): https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Arts-Honours-in-Corrections-Management-%2898655%29
