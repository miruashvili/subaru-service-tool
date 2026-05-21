package com.subaru.servicetool.data.model

enum class IssueSeverity { CRITICAL, HIGH, MEDIUM }

data class KnownIssue(
    val id: String,
    val name: LocalizedText,
    val dtcCodes: List<String>,
    val symptoms: LocalizedText,
    val diagnosticCheck: LocalizedText,
    val fix: LocalizedText,
    val severity: IssueSeverity,
    /** CAN header to switch to before Mode 03 during an active check. Null = no live check. */
    val checkHeader: String? = null,
)

object KnownIssueRegistry {

    val ALL: List<KnownIssue> = listOf(

        KnownIssue(
            id = "CVT_AWD_SOLENOID",
            name = LocalizedText(
                en = "CVT AWD Transfer Clutch Solenoid",
                ka = "CVT AWD გადამცემი მჭიდრო სოლენოიდი",
                ru = "Соленоид муфты AWD в CVT",
                es = "Solenoide de Embrague de Transferencia AWD CVT",
                fr = "Solénoïde d'Embrayage de Transfert AWD CVT",
                de = "CVT AWD Übertragungs-Kupplungssolenoid",
            ),
            dtcCodes = listOf("P0971", "P2764"),
            symptoms = LocalizedText(
                en = "All-Wheel Drive (AWD) system is completely disengaged — rear wheels not receiving power. AT Oil Temp light flashing.",
                ka = "AWD სისტემა სრულად გათიშულია — უკანა თვლები არ იღებენ სიმძლავრეს. AT Oil Temp ნათური ციმციმებს.",
                ru = "Система AWD полностью отключена — задние колёса не получают мощность. Мигает индикатор AT Oil Temp.",
                es = "El sistema AWD está completamente desconectado — las ruedas traseras no reciben potencia. La luz AT Oil Temp parpadea.",
                fr = "Le système AWD est complètement désengagé — les roues arrière ne reçoivent pas de puissance. Le voyant AT Oil Temp clignote.",
                de = "AWD-System vollständig deaktiviert — Hinterräder erhalten keine Leistung. AT Oil Temp-Leuchte blinkt.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Query TCM (7E1) for P0971 / P2764. Check solenoid resistance: should be 3–4 Ω. Open circuit = failed solenoid.",
                ka = "TCM (7E1) შეამოწმეთ P0971 / P2764. სოლენოიდის წინაღობა: 3–4 Ω. ღია ჩართვა = გაფუჭებული სოლენოიდი.",
                ru = "Запросить ТСМ (7E1) для P0971 / P2764. Проверить сопротивление соленоида: 3–4 Ом. Обрыв = неисправный соленоид.",
                es = "Consultar TCM (7E1) para P0971 / P2764. Verificar resistencia del solenoide: 3–4 Ω. Circuito abierto = solenoide defectuoso.",
                fr = "Interroger TCM (7E1) pour P0971 / P2764. Vérifier la résistance du solénoïde: 3–4 Ω. Circuit ouvert = solénoïde défectueux.",
                de = "TCM (7E1) für P0971 / P2764 abfragen. Solenoidwiderstand prüfen: 3–4 Ω. Unterbrechung = defekter Solenoid.",
            ),
            fix = LocalizedText(
                en = "The CVT transfer clutch duty solenoid has electrical failure and requires replacement. Part: 31706AA030/031/032/033.",
                ka = "CVT გადამცემი მჭიდრო სოლენოიდს აქვს ელექტრული გაუმართაობა და საჭიროებს შეცვლას. ნაწილი: 31706AA030/031/032/033.",
                ru = "Соленоид муфты передачи CVT имеет электрическую неисправность и требует замены. Артикул: 31706AA030/031/032/033.",
                es = "El solenoide de embrague de transferencia CVT tiene falla eléctrica y requiere reemplazo. Pieza: 31706AA030/031/032/033.",
                fr = "Le solénoïde d'embrayage de transfert CVT a une défaillance électrique et doit être remplacé. Pièce: 31706AA030/031/032/033.",
                de = "Der CVT-Übertragungs-Kupplungssolenoid hat einen elektrischen Fehler und muss ersetzt werden. Teilenr.: 31706AA030/031/032/033.",
            ),
            severity = IssueSeverity.CRITICAL,
            checkHeader = "7E1",
        ),

        KnownIssue(
            id = "CVT_LOCKUP_SOLENOID",
            name = LocalizedText(
                en = "Torque Converter Lock-Up Solenoid",
                ka = "ბრუნვის გამდიდრებლის ჩაკეტვის სოლენოიდი",
                ru = "Соленоид блокировки гидротрансформатора",
                es = "Solenoide de Bloqueo del Convertidor de Par",
                fr = "Solénoïde de Verrouillage du Convertisseur de Couple",
                de = "Drehmomentwandler-Überbrückungs-Solenoid",
            ),
            dtcCodes = listOf("P2763", "P2764", "P0700"),
            symptoms = LocalizedText(
                en = "Jerking/shuddering when slowing to a stop. Engine wants to stall at low speed. AT OIL TEMP light flashes.",
                ka = "შეკრთომა/გაბზარვა გაჩერებამდე შენელებისას. ძრავა ჩაქრობას ცდილობს დაბალ სიჩქარეზე. AT OIL TEMP ნათური ციმციმებს.",
                ru = "Рывки/вибрация при замедлении до остановки. Двигатель пытается заглохнуть на низкой скорости. Мигает индикатор AT OIL TEMP.",
                es = "Sacudidas/vibraciones al reducir la velocidad hasta detenerse. El motor quiere apagarse a baja velocidad. La luz AT OIL TEMP parpadea.",
                fr = "Secousses/vibrations lors du freinage jusqu'à l'arrêt. Le moteur veut caler à basse vitesse. Le voyant AT OIL TEMP clignote.",
                de = "Rucken/Vibrieren beim Abbremsen bis zum Stillstand. Motor droht bei niedriger Drehzahl abzuwürgen. AT OIL TEMP-Leuchte blinkt.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Check solenoid resistance: should be 12–13 Ω at 20°C. Heat solenoid — if resistance goes to open circuit when hot, solenoid is faulty.",
                ka = "შეამოწმეთ სოლენოიდის წინაღობა: უნდა იყოს 12–13 Ω 20°C-ზე. გაათბეთ სოლენოიდი — თუ გათბობის შემდეგ წინაღობა ხდება ღია, სოლენოიდი გაფუჭებულია.",
                ru = "Проверить сопротивление соленоида: должно быть 12–13 Ом при 20°C. Нагреть соленоид — если при нагреве сопротивление уходит в обрыв, соленоид неисправен.",
                es = "Verificar resistencia del solenoide: debe ser 12–13 Ω a 20°C. Calentar el solenoide — si la resistencia se va a circuito abierto en caliente, el solenoide está defectuoso.",
                fr = "Vérifier la résistance du solénoïde: doit être 12–13 Ω à 20°C. Chauffer le solénoïde — si la résistance passe en circuit ouvert à chaud, le solénoïde est défectueux.",
                de = "Solenoidwiderstand prüfen: soll 12–13 Ω bei 20°C betragen. Solenoid aufwärmen — wenn der Widerstand beim Erwärmen auf Unterbrechung geht, ist der Solenoid defekt.",
            ),
            fix = LocalizedText(
                en = "Replace lock-up solenoid in valve body. Resistance spec: 12 Ω. After replacement: perform CVT fluid change.",
                ka = "შეცვალეთ ჩაკეტვის სოლენოიდი სარქვლის ბლოკში. წინაღობის სპეციფ.: 12 Ω. შეცვლის შემდეგ: CVT-ის სითხის შეცვლა.",
                ru = "Заменить соленоид блокировки в гидравлическом блоке. Спецификация сопротивления: 12 Ом. После замены: замена масла CVT.",
                es = "Reemplazar solenoide de bloqueo en el cuerpo de válvulas. Especificación de resistencia: 12 Ω. Después del reemplazo: cambio de fluido CVT.",
                fr = "Remplacer le solénoïde de verrouillage dans le corps de valve. Spécification de résistance: 12 Ω. Après remplacement: vidange fluide CVT.",
                de = "Überbrückungs-Solenoid im Ventilblock ersetzen. Widerstandsspezifikation: 12 Ω. Nach dem Austausch: CVT-Ölwechsel.",
            ),
            severity = IssueSeverity.HIGH,
            checkHeader = "7E1",
        ),

        KnownIssue(
            id = "TCV_THERMOSTAT",
            name = LocalizedText(
                en = "Thermo Control Valve (TCV) — Electric Thermostat",
                ka = "თერმული კონტროლის სარქველი (TCV) — ელექტრო თერმოსტატი",
                ru = "Клапан термического управления (TCV) — электронный термостат",
                es = "Válvula de Control Térmico (TCV) — Termostato Eléctrico",
                fr = "Vanne de Contrôle Thermique (TCV) — Thermostat Électrique",
                de = "Thermisches Steuerventil (TCV) — Elektrischer Thermostat",
            ),
            dtcCodes = listOf("P26A3", "P26A5", "P2682"),
            symptoms = LocalizedText(
                en = "Engine takes too long to warm up. Poor fuel economy. Heater blows cool air. EyeSight may disable in cold weather. Radiator fans running continuously at maximum speed.",
                ka = "ძრავა ძალიან დიდ ხანს ათბობს. საწვავის ცუდი ხარჯი. გამათბობელი ცივ ჰაერს ბერავს. EyeSight შეიძლება გამოირთოს ცივ ამინდში. რადიატორის ვენტილატორები მუდმივად მუშაობს მაქსიმალური სიჩქარით.",
                ru = "Двигатель долго прогревается. Повышенный расход топлива. Печка дует холодным воздухом. EyeSight может отключиться в холодную погоду. Вентиляторы радиатора работают непрерывно на максимальной скорости.",
                es = "El motor tarda demasiado en calentarse. Mayor consumo de combustible. La calefacción sopla aire frío. EyeSight puede desactivarse en clima frío. Los ventiladores del radiador funcionan continuamente a máxima velocidad.",
                fr = "Le moteur met trop longtemps à chauffer. Mauvaise consommation de carburant. Le chauffage souffle de l'air froid. EyeSight peut se désactiver par temps froid. Les ventilateurs du radiateur fonctionnent en continu à vitesse maximale.",
                de = "Motor braucht zu lange zum Aufwärmen. Schlechter Kraftstoffverbrauch. Heizung bläst kalte Luft. EyeSight kann bei Kälte deaktiviert werden. Kühlergebläse laufen kontinuierlich mit maximaler Drehzahl.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Query ECM (7E0) for P26A3, P26A5, P2682. Check TSB 09-75-21 ECM software update — must be applied first.",
                ka = "ECM (7E0) შეამოწმეთ P26A3, P26A5, P2682. TSB 09-75-21 ECM პროგრამული განახლება — ჯერ უნდა გამოყენებულ იქნას.",
                ru = "Запросить ЭБУ (7E0) для P26A3, P26A5, P2682. Обновление ПО ЭБУ TSB 09-75-21 — должно быть применено первым.",
                es = "Consultar ECM (7E0) para P26A3, P26A5, P2682. Actualización de software ECM TSB 09-75-21 — debe aplicarse primero.",
                fr = "Interroger ECM (7E0) pour P26A3, P26A5, P2682. Mise à jour logicielle ECM TSB 09-75-21 — doit être appliquée en premier.",
                de = "ECM (7E0) für P26A3, P26A5, P2682 abfragen. ECM-Software-Update TSB 09-75-21 — muss zuerst angewendet werden.",
            ),
            fix = LocalizedText(
                en = "The Thermo Control Valve component is defective and requires mechanical replacement. Subaru extended warranty coverage to 15 years/150,000 miles under TSB 09-75-21.",
                ka = "TCV კომპონენტი გაფუჭებულია და საჭიროებს მექანიკურ შეცვლას. Subaru-მ გაახანგრძლივა გარანტია 15 წლამდე/150,000 მილამდე TSB 09-75-21-ის მიხედვით.",
                ru = "Компонент TCV неисправен и требует механической замены. Subaru продлила гарантийное покрытие до 15 лет/150 000 миль по TSB 09-75-21.",
                es = "El componente TCV es defectuoso y requiere reemplazo mecánico. Subaru extendió la cobertura de garantía a 15 años/150,000 millas bajo TSB 09-75-21.",
                fr = "Le composant TCV est défectueux et nécessite un remplacement mécanique. Subaru a étendu la couverture de garantie à 15 ans/150 000 miles sous TSB 09-75-21.",
                de = "Die TCV-Komponente ist defekt und erfordert einen mechanischen Austausch. Subaru hat die Garantieabdeckung auf 15 Jahre/150.000 Meilen gemäß TSB 09-75-21 verlängert.",
            ),
            severity = IssueSeverity.HIGH,
            checkHeader = "7E0",
        ),

        KnownIssue(
            id = "CVT_ASCENT_PRESSURE",
            name = LocalizedText(
                en = "CVT Pressure Control Issue",
                ka = "CVT-ის წნევის კონტროლის პრობლემა",
                ru = "Проблема управления давлением CVT",
                es = "Problema de Control de Presión CVT",
                fr = "Problème de Contrôle de Pression CVT",
                de = "CVT-Drucksteuerungsproblem",
            ),
            dtcCodes = listOf("P0841", "P0868", "P0700"),
            symptoms = LocalizedText(
                en = "Transmission shudder, hesitation, and limp mode.",
                ka = "გადამცემის ვიბრაცია, დაყოვნება და გადარჩენის რეჟიმი.",
                ru = "Вибрация трансмиссии, задержки и аварийный режим.",
                es = "Vibración de transmisión, dudas y modo de emergencia.",
                fr = "Vibrations de la transmission, hésitations et mode de sécurité.",
                de = "Getriebebeben, Zögerlichkeit und Notlaufprogramm.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Check transmission fluid level and condition. Scan for stored DTCs in TCU (7E1).",
                ka = "შეამოწმეთ გადამცემის სითხის დონე და მდგომარეობა. TCU (7E1) DTC-ების სკანირება.",
                ru = "Проверить уровень и состояние масла в трансмиссии. Считать коды ошибок в ТСМ (7E1).",
                es = "Verificar nivel y condición del fluido de transmisión. Escanear DTCs en el TCU (7E1).",
                fr = "Vérifier le niveau et l'état du liquide de transmission. Scanner les DTCs dans le TCU (7E1).",
                de = "Getriebeflüssigkeitsstand und -zustand prüfen. DTCs im TCU (7E1) auslesen.",
            ),
            fix = LocalizedText(
                en = "Check Subaru warranty extension (Hickman v. Subaru settlement 2024 — 100,000 mile warranty extension for affected Ascents).",
                ka = "შეამოწმეთ Subaru-ს გარანტიის გახანგრძლივება (Hickman v. Subaru 2024 — 100,000 მილის გარანტია Ascent-ებისთვის).",
                ru = "Проверить продление гарантии Subaru (Hickman v. Subaru 2024 — 100 000 миль для Ascent).",
                es = "Verificar extensión de garantía Subaru (Hickman v. Subaru 2024 — 100,000 millas para Ascents afectados).",
                fr = "Vérifier l'extension de garantie Subaru (Hickman v. Subaru 2024 — 100 000 miles pour les Ascent concernés).",
                de = "Subaru-Garantieverlängerung prüfen (Hickman v. Subaru 2024 — 100.000 Meilen für betroffene Ascents).",
            ),
            severity = IssueSeverity.HIGH,
            checkHeader = "7E1",
        ),
    )

    private val byId = ALL.associateBy { it.id }

    fun findById(id: String): KnownIssue? = byId[id]

    fun findByDtcCode(code: String): KnownIssue? =
        ALL.firstOrNull { code in it.dtcCodes }
}
