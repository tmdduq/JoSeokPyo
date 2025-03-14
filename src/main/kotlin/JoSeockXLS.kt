import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.util.IOUtils
import org.apache.poi.xssf.usermodel.*
import java.awt.Desktop
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap

class JoSeockXLS {

    val commonMap = mutableMapOf<String, String?>(
        "rstCode" to "0",
        "rstTitle" to null,
        "rstMessage" to null,
        "errorMessage" to null,
    )

    private val moonPhasePath = "src/main/resources/moonPhase"
    private val tideFilePath = "src/main/resources/TIDE(2025-2026).csv"
    fun downloadXLS(startDate: String, endDate: String) :  MutableMap<String, String?>{
        val map = getJoSeckMap(startDate,endDate)
        if(map==null){
            val rstMap = commonMap.toMutableMap()
            rstMap["rstTitle"] = "오류가 발생했어요"
            rstMap["rstMessage"] = "프로그램 파일이 깨진 것 같아요."
            return rstMap
        }
        return processXLS(map)
    }

    private fun getJoSeckMap(startDate: String, endDate: String): HashMap<String, MutableList<List<String>>>? {
        try {
            val data = readCsvFile(tideFilePath)
            val map = HashMap<String, MutableList<List<String>>>()

            val listDate = getDateRange(startDate, endDate)
            listDate.forEach { map[it] = mutableListOf() }

            for (row in data) {
                if (row.isEmpty()) break
                if (row[1] !in listDate) continue
                map[row[1]]?.add(row)
            }
            return map
        }catch(e : Exception){
            return null
        }
    }


    private fun readCsvFile(filePath: String): List<List<String>> {
        val file = File(filePath)
        val result = mutableListOf<List<String>>()

        file.forEachLine { line ->
            val columns = line.split(",").map { it.trim() }
            result.add(columns)
        }
        return result
    }

    private fun getDateRange(startString: String, endString: String): List<String> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startDate = LocalDate.parse(startString, formatter)
        val endDate = LocalDate.parse(endString, formatter)

        return generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .map { it.format(formatter) }
            .toList()
    }

    private fun processXLS(map: HashMap<String, MutableList<List<String>>>): MutableMap<String, String?> {
        val rstMap = commonMap.toMutableMap()
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("조석표")

        var cellIndex = 0

        // 0행 제목 표시줄
        val headTitleStyle = getBasicStyle(workbook = workbook, isBorder = false)
        val headTitleFont = workbook.createFont()
        headTitleFont.bold = true
        headTitleFont.fontHeight = (28 * 20).toShort()
        headTitleStyle.setFont(headTitleFont)
        getRow(sheet, 0).createCell(3).setCellValue("조석표")
        getRow(sheet, 0).getCell(3).cellStyle = headTitleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 3, 5))
        sheet.getRow(0).height =640

        // 1행 표 제목줄
        val graphTitleStyle = cellBackgroundColor(getBasicStyle(workbook = workbook, isBorder = false), 242, 242, 242)
        val graphTitleFont = workbook.createFont()
        graphTitleFont.bold = true
        graphTitleFont.fontHeight = (14 * 20).toShort()
        graphTitleStyle.setFont(graphTitleFont)
        var row = sheet.createRow(1)
        listOf("", "인천", "", "연평도", "", "대청도", "", "백령도", "").forEach {
            row.createCell(cellIndex++).setCellValue(it)
            row.getCell(cellIndex - 1).cellStyle =
                totalBorder(graphTitleStyle, listOf("top", "bottom", "left", "right"))
        }
        sheet.addMergedRegion(CellRangeAddress(1, 1, 1, 2))
        sheet.addMergedRegion(CellRangeAddress(1, 1, 3, 4))
        sheet.addMergedRegion(CellRangeAddress(1, 1, 5, 6))
        sheet.addMergedRegion(CellRangeAddress(1, 1, 7, 8))


        //Map에 들어있는 값을 날짜순으로 정렬
        val dayList = map.keys.sortedBy { it }.toMutableList()

        //우측상단 날짜표기
        getRow(sheet, 0).createCell(6)
            .setCellValue("${formatDate(dayList.first())} ~ ${formatDate(dayList.last())} / ${dayList.size}일간")
        getRow(sheet, 0).getCell(6).cellStyle = getBasicStyle(workbook = workbook, isBorder = false)
        sheet.addMergedRegion(CellRangeAddress(0, 0, 6, 8))

        val dayListLength = dayList.size
        var rowIndex = 2
        for (day in dayList) {
            val lineList = map[day]?.sortedBy { it[0] }?.toMutableList()
            if(lineList.isNullOrEmpty() ){
                rstMap["rstTitle"] = "오류가 발생했습니다."
                rstMap["rstMessage"] = "해당날짜 데이터가 없습니다."
                return rstMap
            }
            row = getRow(sheet, rowIndex)
            for (line in lineList) { // [대청도, 2025-03-17, 01:51/저/45, 07:51/고/336, 14:09/저/56, 19:55/고/315, 2025-02-18, 목사리, 아홉매, 열물]
                val name = line[0]
                val tideName = line[9]
                val sunRise = formatDate(line[10], "hh:mm:ss a", "HH:mm")
                val sunSet = formatDate(line[11], "hh:mm:ss a", "HH:mm")
                var index = 0

                when (name) {
                    "인천항" -> index = 1
                    "연평도" -> index = 3
                    "대청도" -> index = 5
                    "백령도" -> index = 7
                    else -> println(name)
                }

                getRow(sheet, rowIndex).createCell(0).setCellValue("${formatDate(day)}\r\n$tideName") // 일자

                for (n in 2..5) { // [n=2] 01:51/저/45, [3] 07:51/고/336, [4] 14:09/저/56, [5] 19:55/고/315
                    val v = line[n].split("/")  //  01:51/저/45
                    val tideTime = if(v[0].startsWith("--")) "ㅡ" else v[0]
                    val tideType = v[1]
                    val tideDepth = if(v[2].startsWith("--")) "ㅡ" else "${v[2]}cm"
                    var cellStyle = when (tideType) {
                        "고" -> cellStyleColor(workbook, 255, 0, 0)
                        "저" -> cellStyleColor(workbook, 0, 0, 255)
                        else -> cellStyleColor(workbook, 0, 0, 0)
                    }
                    if(tideDepth.startsWith("-"))
                        cellStyle = cellBackgroundColor(cellStyle,251,248,215)
                    cellStyle = getBasicStyle(basicStyle = cellStyle)
                    //Day 경계선
                    if (n == 5) cellStyle = totalBorder(cellStyle.copy(), listOf("bottom"))

                    // 시간,높이 입력
                    getRow(sheet, rowIndex + n - 1).createCell(index).setCellValue(tideTime)
                    getRow(sheet, rowIndex + n - 1).createCell(index + 1).setCellValue(tideDepth)
                    getRow(sheet, rowIndex + n - 1).getCell(index).cellStyle = cellStyle
                    getRow(sheet, rowIndex + n - 1).getCell(index + 1).cellStyle =
                        totalBorder(cellStyle.copy(), listOf("right"))
                } // end 조위값 입력

                //일출일몰 입력
                val wrapTextStyle = cellBackgroundColor(getBasicStyle(workbook = workbook), 221, 235, 247)
                val wrapTextFont = workbook.createFont()
                wrapTextFont.fontHeight = (14 * 20).toShort()
                wrapTextStyle.setFont(wrapTextFont)
                getRow(sheet, rowIndex).createCell(index).setCellValue("$sunRise ~ $sunSet")
                getRow(sheet, rowIndex).getCell(index).cellStyle = wrapTextStyle
                getRow(sheet, rowIndex).createCell(index+1).cellStyle = totalBorder(wrapTextStyle.copy(), listOf("right"))
                sheet.addMergedRegion(CellRangeAddress(rowIndex, rowIndex, index, index+1))

            } //end line

            //A열 일자 테두리
            val wrapTextStyle = cellBackgroundColor(getBasicStyle(workbook = workbook), 242, 242, 242)
            getRow(sheet, rowIndex).getCell(0).cellStyle = totalBorder(wrapTextStyle.copy(), listOf("left", "right"))
            for(n in 1..4) {
                val borderPoint = if(n==4)  listOf("left", "right", "bottom") else listOf("left", "right")
                getRow(sheet, rowIndex + n).createCell(0)
                    .cellStyle = totalBorder(wrapTextStyle.copy(), borderPoint)
            }

            //A열 일자 5줄 병합
            sheet.addMergedRegion(CellRangeAddress(rowIndex, rowIndex + 4, 0, 0))

            // 행 높이 // 컨텐츠 갯수별 가변행높이
            val rowHeight =
                if(dayListLength<6) 600
                else if(dayListLength<7) 540
                else if(dayListLength<8) 470
                else if(dayListLength<10)  400
                else 600
            val imageOffset =
                if(dayListLength<6) 70000
                else if(dayListLength<7) 70000
                else if(dayListLength<8) 0
                else if(dayListLength<10) -90000
                else 70000

            // 이미지 파일 읽기
            val imageBytes = IOUtils.toByteArray(FileInputStream( getMoonPhasePath(day) ))
            val pictureIndex = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG)
            // 그림을 삽입할 위치 정의
            val drawing: XSSFDrawing = sheet.createDrawingPatriarch() as XSSFDrawing
            val anchor: XSSFClientAnchor = drawing.createAnchor(
                (2048*2.2 *256/7).toInt(), imageOffset,
                0, 0, 0, rowIndex+1, 1, rowIndex+3)
            drawing.createPicture(anchor, pictureIndex).resize(0.99,0.65)

            for (n in rowIndex until rowIndex+5)
                sheet.getRow(n)?.let{
                    it.height = if(n==rowIndex) 300 else rowHeight.toShort()
                }

            rowIndex += 5
        } // end for(day in dayList)

        //컨텐츠가 너무 많으면 1장인쇄가 찌그러져서 가로 크게
        if(dayListLength in 7..9)
            for(n in 1..8)
                sheet.setColumnWidth(n, (sheet.getColumnWidth(n)*1.2).toInt() )
        // 10일 이상이면 2페이지로 분할해서 뽑을꺼니까 가로 사이즈를 안늘려도 돼
        else if(dayListLength>9 || dayListLength<6)
            sheet.getRow(0).height = 800

        workbook.setPrintArea(0, 0, 8, 0, rowIndex - 1)
        val printSetup = sheet.printSetup
        printSetup.topMargin = 0.3 // 위쪽 여백
        printSetup.bottomMargin = 0.3 // 아래쪽 여백
        printSetup.paperSize = PrintSetup.A4_PAPERSIZE
        sheet.fitToPage = true
        sheet.repeatingRows = CellRangeAddress(0,1,0,8) //제목셀

        if(dayListLength>9) { //10일 이상이면 2페이지로 분할
            for(n in 1..dayListLength/5)
                sheet.setRowBreak(25*n+1)
            printSetup.fitHeight = 0    // 높이 자동
            printSetup.fitWidth = 1    // 페이지 너비 맞추기
            printSetup.leftToRight = true
            sheet.autobreaks = true
        }

        val desktopPath = getDesktopPath()
        val fileName = "조석표 (${formatDate(dayList.first(), outFormat = "MMdd")}-${formatDate(dayList.last(), outFormat = "MMdd")}).xlsx"
        try {
            val fileOut = FileOutputStream("$desktopPath/$fileName")
            workbook.write(fileOut)
            fileOut.close()
        }catch(e : FileNotFoundException){
            e.printStackTrace()
            rstMap["rstTitle"] = "오류가 발생했습니다."
            rstMap["rstMessage"] = "같은 이름의 파일이 이미 실행중인 것 같아요.\n바탕화면 : $fileName"
            rstMap["errorMessage"] = "$e"
            return rstMap
        } catch (e: IOException) {
            e.printStackTrace()
            rstMap["rstTitle"] = "오류가 발생했습니다."
            rstMap["rstMessage"] = "파일을 만들 수 없습니다. 관리자 권한으로 실행해보세요."
            rstMap["errorMessage"] = "$e"
            return rstMap
        } finally {
            try {
                workbook.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val xlsFile = File("$desktopPath/$fileName")
        try {
            if (xlsFile.exists() && Desktop.isDesktopSupported())
                Desktop.getDesktop().open(xlsFile)
        } catch (e: Exception) {
            rstMap["rstTitle"] = "오류가 발생했습니다."
            rstMap["rstMessage"] = "파일을 실행할 수 없네요. 바탕화면에 조석표.xlxs를 직접 실행해보세요."
            rstMap["errorMessage"] = "$e"
            return rstMap
        }
        rstMap["rstCode"] = "1"
        rstMap["rstTitle"] = "조석표를 만들었어요!"
        rstMap["rstMessage"] = "파일은 바탕화면에 저장됐어요. \n$fileName"
        return rstMap
    }

    fun getDesktopPath(): String {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome, "Desktop").toString() // 바탕화면 경로 생성
    }

    private fun getRow(sheet: XSSFSheet, rowIndex: Int) = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)

    private fun cellStyleColor(workbook: XSSFWorkbook, R: Int, G: Int, B: Int): XSSFCellStyle {
        val cellStyle = workbook.createCellStyle()
        val font = workbook.createFont()
        font.fontHeight = (14 * 20).toShort()
        val color = XSSFColor(byteArrayOf(R.toByte(), G.toByte(), B.toByte()))
        font.setColor(color)
        cellStyle.setFont(font)
        return cellStyle
    }

    private fun cellBackgroundColor(style: XSSFCellStyle, R: Int, G: Int, B: Int): XSSFCellStyle {
        val rgb = byteArrayOf(R.toByte(), G.toByte(), B.toByte())
        val color = XSSFColor(rgb)
        style.setFillForegroundColor(color)  // 배경색으로 설정
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    private fun getBasicStyle(
        workbook: XSSFWorkbook? = null,
        basicStyle: XSSFCellStyle = workbook!!.createCellStyle(),
        isBorder: Boolean = true
    ): XSSFCellStyle {
        basicStyle.wrapText = true
        basicStyle.alignment = HorizontalAlignment.CENTER  // 수평 가운데
        basicStyle.verticalAlignment = VerticalAlignment.CENTER  // 수직 가운데
        if (!isBorder) return basicStyle
        basicStyle.borderTop = BorderStyle.THIN
        basicStyle.borderBottom = BorderStyle.THIN
        basicStyle.borderLeft = BorderStyle.THIN
        basicStyle.borderRight = BorderStyle.THIN
        basicStyle.topBorderColor = IndexedColors.BLACK.index
        basicStyle.bottomBorderColor = IndexedColors.BLACK.index
        basicStyle.leftBorderColor = IndexedColors.BLACK.index
        basicStyle.rightBorderColor = IndexedColors.BLACK.index
        return basicStyle
    }


    private fun totalBorder(
        basicStyle: XSSFCellStyle,
        position: List<String>,
        border: BorderStyle = BorderStyle.THICK
    ): XSSFCellStyle {
        if ("top" in position) basicStyle.borderTop = border
        if ("left" in position) basicStyle.borderLeft = border
        if ("right" in position) basicStyle.borderRight = border
        if ("bottom" in position) basicStyle.borderBottom = border
        return basicStyle
    }


    private fun formatDate(inputDate: String, inFormat:String="yyyy-MM-dd" ,outFormat : String="M. d.(E)"): String? {
        try {
            val inputFormat = SimpleDateFormat(inFormat, Locale.US)
            val date = inputFormat.parse(inputDate)

            val outputFormat = SimpleDateFormat(outFormat, Locale.KOREAN)
            return outputFormat.format(date)
        }catch(e: Exception){
            e.printStackTrace()
            return null }
    }


    fun getMoonPhasePath(dateString: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val date = LocalDate.parse(dateString, formatter)

        val daysSinceNewMoon = ChronoUnit.DAYS.between(LocalDate.of(2025, 2, 27), date)
        val phaseInCycle = (daysSinceNewMoon % 29.5305882).toInt()
        return "${moonPhasePath}/moon${phaseInCycle}.png"
    }
}