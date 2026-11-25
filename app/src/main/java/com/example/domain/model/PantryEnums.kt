package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LocationType(val labelIt: String, val sortOrder: Int) {
    FRIDGE("Frigorifero", 0),
    FREEZER("Freezer", 1),
    PANTRY("Dispensa", 2),
    OTHER("Altro", 3)
}

@Serializable
enum class UnitOfMeasure(val symbol: String, val labelIt: String, val isDiscrete: Boolean = false) {
    PIECES("pz", "Pezzi", true),
    GRAMS("g", "Grammi"),
    KILOGRAMS("kg", "Chilogrammi"),
    MILLILITERS("ml", "Millilitri"),
    LITERS("l", "Litri"),
    PACKS("conf", "Confezioni", true),
    CANS("latt", "Lattine", true),
    BOTTLES("bott", "Bottiglie", true),
    JARS("vasetti", "Vasetti", true),
    SLICES("fette", "Fette", true),
    SPOONS("cucchiai", "Cucchiai")
}

@Serializable
enum class ExpiryType(val labelIt: String, val shortLabel: String, val infoIt: String) {
    USE_BY(
        labelIt = "Data di scadenza",
        shortLabel = "Scade il",
        infoIt = "Indica la data entro cui l'alimento deperibile va consumato."
    ),
    BEST_BEFORE(
        labelIt = "Termine minimo di conservazione (TMC)",
        shortLabel = "Da consumarsi pref.",
        infoIt = "Indica la data fino alla quale il prodotto conserva le proprietà organolettiche ottimali."
    )
}

@Serializable
enum class ExpiryStatus(val labelIt: String) {
    EXPIRED("Scaduto"),
    EXPIRING_TODAY("Scade oggi"),
    EXPIRING_SOON("In scadenza"),
    FRESH("Fresco"),
    NO_EXPIRY("Senza scadenza")
}

@Serializable
enum class MovementType(val labelIt: String, val isInbound: Boolean) {
    PURCHASE("Acquisto", true),
    MANUAL_ADD("Inserimento manuale", true),
    CONSUME("Consumo", false),
    WASTE("Spreco", false),
    ADJUSTMENT("Rettifica inventario", false),
    TRANSFER("Trasferimento posizione", false),
    BATCH_CANCEL("Annullamento lotto", false)
}

@Serializable
enum class HouseInfoCategory(
    val labelIt: String,
    val shortLabelIt: String,
    val subtitleHintIt: String
) {
    PHONE_NUMBER(
        labelIt = "Numeri utili",
        shortLabelIt = "Numeri",
        subtitleHintIt = "Ruolo o ditta (es. Idraulico, Amministratore)"
    ),
    MEASUREMENT(
        labelIt = "Misure casa",
        shortLabelIt = "Misure",
        subtitleHintIt = "Stanza o elemento (es. Finestra camera, Nicchia frigo)"
    ),
    PHOTO_DETAIL(
        labelIt = "Foto dettagli",
        shortLabelIt = "Foto",
        subtitleHintIt = "Zona o impianto (es. Quadro elettrico, Valvola gas)"
    ),
    PASSWORD_NOTE(
        labelIt = "Password & Note",
        shortLabelIt = "Password",
        subtitleHintIt = "Servizio o dispositivo (es. Wi-Fi, Allarme, Cancello)"
    )
}

@Serializable
enum class MeasurementUnit(val symbol: String, val labelIt: String) {
    CM("cm", "Centimetri (cm)"),
    MM("mm", "Millimetri (mm)"),
    M("m", "Metri (m)")
}

@Serializable
enum class ThemePreference(val labelIt: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro")
}

enum class PantrySortOption(val labelIt: String) {
    NAME_ASC("Alfabetico (A-Z)"),
    EXPIRY_ASC("Scadenza più vicina"),
    QUANTITY_DESC("Quantità maggiore"),
    QUANTITY_ASC("Quantità minore")
}

enum class StatsPeriod(val labelIt: String) {
    WEEK("Settimana"),
    MONTH("Mese"),
    YEAR("Anno"),
    CUSTOM("Personalizzato")
}
