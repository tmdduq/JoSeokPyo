import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.LazyThreadSafetyMode.NONE
import ContinuousSelectionHelper.isInDateBetweenSelection
import ContinuousSelectionHelper.isOutDateBetweenSelection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.*
import com.kizitonwose.calendar.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DayOfWeekNames
import java.awt.Desktop
import java.io.File
import java.time.format.TextStyle
import java.util.*
import kotlin.collections.ArrayList


private val selectionColor = Color(10,21,75).copy(alpha = 0.9f)
private val continuousSelectionColor = Color(11,64,148).copy(alpha = 0.2f)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        resizable = false,
        title = "Tide Generator",
        icon =  painterResource("tide.ico"),
        state = rememberWindowState(width = 400.dp, height = 530.dp ))
    {
            App()
    } //Window
}

@Composable
fun App(){
    val coroutineScope = rememberCoroutineScope()   // 현재 스코프
    val xlsRstMap = remember{ mutableStateOf(JoSeockXLS().commonMap.toMutableMap()) } // 엑셀생성 결과Map
    val regionList = remember { mutableStateOf( emptyMap<String, ArrayList<String>>() ) } // 모든 지점 List

    val selectedRegionList = remember { mutableStateOf( mutableListOf<String>()  ) } //선택된 지점 List

    val currentMonth = remember { YearMonth.now() } //첫화면에 표시할 월
    val today = remember { LocalDate.now() } // 오늘날짜
    val startMonth = remember { currentMonth.minusMonths(2) } //과거 2달치 달력부터 출력
    val endMonth = remember { currentMonth.plusMonths(6) } // 미래 6개월치 달력까지 출력
    var selection by remember { mutableStateOf(DateSelection()) } // selection : (startDate, endDate)
    val daysOfWeek = remember { daysOfWeek() }

    //대상지점들 불러오기
    LaunchedEffect(Unit) {
        JoSeockXLS().readRegion()?.let {
            selectedRegionList.value = it.toMutableList()}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column {
            val state = rememberCalendarState(
                startMonth = startMonth,
                endMonth = endMonth,
                firstVisibleMonth = currentMonth,
                firstDayOfWeek = daysOfWeek.first(),
            )

            //상단, 지점명 + 요일명 전시
            CalendarTop(
                daysOfWeek = daysOfWeek,
                selection = selection,
                selectedRegionList = selectedRegionList.value,
                setRegion = {
                    coroutineScope.launch{ regionList.value = JoSeockXLS().getRegionList() }
                },
                clearDates = { selection = DateSelection() },
            )

            // 달력
            VerticalCalendar(
                state = state,
                monthHeader = { month -> MonthHeader(month) },
                dayContent = { value ->
                    Day(
                        value,
                        today = today,
                        selection = selection,
                    ) { day ->
                        if (day.position == DayPosition.MonthDate && day.date >= today) {
                            selection = ContinuousSelectionHelper.getSelection(
                                clickedDate = day.date,
                                dateSelection = selection,
                            )
                        }
                    }
                },
                contentPadding = PaddingValues(bottom = 100.dp),
            ) // end VerticalCalendar
        } // end Column


        //하단바 보이기숨기기
        AnimatedVisibility(
            visible =  (selection.daysBetween!=null) && selectedRegionList.value.isNotEmpty(),
            modifier = Modifier.background(Color(221, 235, 247) ).wrapContentHeight()
                .fillMaxWidth().align(Alignment.BottomCenter),
        ) {
            //하단바
            CalendarBottom(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(238,249,252),  // 하단
                                Color(255,255,255),  // 상단 색상
                            )
                        ))
                        //Color(221, 235, 247) )//.background(Color.White)
                    .align(Alignment.BottomCenter),
                selection = selection,
                save = {
                    val (startDate, endDate) = selection
                    if (startDate != null && endDate != null) {
                        coroutineScope.launch {
                            xlsRstMap.value = xlsRstMap.value.toMutableMap().apply { this["rstTitle"] = "처리중입니다..." }
                            xlsRstMap.value["rstMessage"] = "조석표를 엑셀로 만들고 있어요."
                            xlsRstMap.value = withContext(Dispatchers.IO) {
                                JoSeockXLS().downloadXLS(startDate.toString(), endDate.toString(), selectedRegionList.value)
                            }
                            xlsRstMap.value["fileName"]?.let {
                                val xlsFile = File(it)
                                if (xlsFile.exists() && Desktop.isDesktopSupported())
                                    Desktop.getDesktop().open(xlsFile)
                            }
                        }
                    }
                }
            ) // end CalendarBottom
        } // AnimatedVisibility

        if(regionList.value.isNotEmpty()) {
            regionListView(regionList.value, onClose = {
                selectedRegionList.value = it.toMutableList()
                regionList.value = emptyMap<String, ArrayList<String>>()
            }, selectedRegionList2 = selectedRegionList.value)

        }
    } // Box

    if(!xlsRstMap.value["rstTitle"].isNullOrBlank()){
        makeDialog(xlsRstMap.value, {xlsRstMap.value = JoSeockXLS().commonMap.toMutableMap()})
    }
}

@Composable
private fun Day(
    day: CalendarDay,
    today: LocalDate,
    selection: DateSelection,
    onClick: (CalendarDay) -> Unit,
) {
    var textColor = Color.Transparent
    Box(
        modifier = Modifier
            .aspectRatio(1f) // This is important for square-sizing!
            .clickable(
                enabled = day.position == DayPosition.MonthDate && day.date >= today,
                //showRipple = false,
                onClick = { onClick(day) },
            )
            .backgroundHighlight(
                day = day,
                today = today,
                selection = selection,
                selectionColor = selectionColor,
                continuousSelectionColor = continuousSelectionColor,
            ) { textColor = it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                color = when(dayOfWeek.value){
                    6 -> Color.Blue
                    7 -> Color.Red
                    else -> Color.Black
                },
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            )
        }
    }
}

@Composable
private fun MonthHeader(calendarMonth: CalendarMonth) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            textAlign = TextAlign.Center,
            text = calendarMonth.yearMonth.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CalendarTop(
    modifier: Modifier = Modifier,
    daysOfWeek: List<DayOfWeek>,
    selection: DateSelection,
    selectedRegionList: MutableList<String>,
    setRegion: () -> Unit,
    clearDates: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 1.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val daysBetween = selection.daysBetween
                val text =  if (selectedRegionList.isEmpty()) "추출할 지역을 선택해주세요."
                            else "${selectedRegionList[0]} 등 ${selectedRegionList.size}개 지점"
                Button(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    onClick = {setRegion()},
                    colors= ButtonDefaults.buttonColors(Color(228,228,244,)),
                ){ Text(text, fontWeight = FontWeight.Bold, fontSize = 12.sp,) }

                Spacer(modifier = Modifier.weight(1f))
                Text("made by Osy :)")
                Icon(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .clickable(onClick = clearDates)
                        .padding(12.dp),
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(255,255,255),  // 상단 색상
                                Color(238,249,252),  // 하단
                            )
                        )
                    )
                    .padding(top = 4.dp, bottom = 5.dp),
            ) {
                DaysOfWeekTitle(daysOfWeek = daysOfWeek)
//                for (dayOfWeek in daysOfWeek) {
//                    Text(
//
//                        modifier = Modifier.weight(1f),
//                        textAlign = TextAlign.Center,
//                        color = Color.DarkGray,
//                        text = dayOfWeek.value.toString(),
//                        fontSize = 15.sp,
//                    )
//                }
            }
        }
        // 요일표시 하단 선
        Divider(
            color= Color(0,0,0,255),
            thickness = 2.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CalendarBottom(
    modifier: Modifier = Modifier,
    selection: DateSelection,
    save: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        //footer 경계선
        Divider(
            color= Color(0,0,0,192),
            thickness = 2.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            //footer Text
            Text(
                text = selection.daysBetween?.let { dateRangeDisplayText(selection.startDate!!, selection.endDate!!) }?:"날짜를 골라주세요!",
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                modifier = Modifier.height(40.dp).width(100.dp),
                onClick = save,
                colors = ButtonDefaults.buttonColors(Color(10,21,75)),
                enabled = selection.daysBetween != null,
            ) {
                Text(text = "생성!", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}


data class DateSelection(val startDate: LocalDate? = null, val endDate: LocalDate? = null) {
    val daysBetween by lazy(NONE) {
        if (startDate == null || endDate == null) {
            null
        } else {
            startDate.daysUntil(endDate)
        }
    }
}

val rangeFormatter: DateTimeFormat<LocalDate> = LocalDate.Format {
    year() ; chars(". ") ; monthNumber() ; chars(". ") ; dayOfMonth() ; chars(". ")
    dayOfWeek(DayOfWeekNames(listOf("(월)", "(화)", "(수)", "(목)", "(금)", "(토)", "(일)")))
}
//private val rangeFormatter = LocalDate.Formats.ISO
fun dateRangeDisplayText(startDate: LocalDate, endDate: LocalDate): String {
    return "${rangeFormatter.format(startDate)} ~ ${rangeFormatter.format(endDate)}"
}

object ContinuousSelectionHelper {
    fun getSelection(
        clickedDate: LocalDate,
        dateSelection: DateSelection,
    ): DateSelection {
        val (selectionStartDate, selectionEndDate) = dateSelection
        return if (selectionStartDate != null) {
            if (clickedDate < selectionStartDate || selectionEndDate != null) {
                DateSelection(startDate = clickedDate, endDate = null)
            } else if (clickedDate != selectionStartDate) {
                DateSelection(startDate = selectionStartDate, endDate = clickedDate)
            } else {
                DateSelection(startDate = clickedDate, endDate = null)
            }
        } else {
            DateSelection(startDate = clickedDate, endDate = null)
        }
    }

    fun isInDateBetweenSelection(
        inDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Boolean {
        if (startDate.yearMonth == endDate.yearMonth) return false
        if (inDate.yearMonth == startDate.yearMonth) return true
        val firstDateInThisMonth = inDate.yearMonth.atStartOfMonth()
        return firstDateInThisMonth in startDate..endDate && startDate != firstDateInThisMonth
    }

    fun isOutDateBetweenSelection(
        outDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Boolean {
        if (startDate.yearMonth == endDate.yearMonth) return false
        if (outDate.yearMonth == endDate.yearMonth) return true
        val lastDateInThisMonth = outDate.yearMonth.atEndOfMonth()
        return lastDateInThisMonth in startDate..endDate && endDate != lastDateInThisMonth
    }
}



//시작-종료날짜 선택시 사이 날짜에 음영이 들어가는데, 시작일, 종료일에는 음영을 반만 그리기 위한 것
private class HalfSizeShape(private val clipStart: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val half = size.width / 2f
        val offset = if (layoutDirection == LayoutDirection.Ltr) {
            if (clipStart) Offset(half, 0f) else Offset.Zero
        } else {
            if (clipStart) Offset.Zero else Offset(half, 0f)
        }
        return Outline.Rectangle(Rect(offset, Size(half, size.height)))
    }
}


//날짜 표시
fun Modifier.backgroundHighlight(
    day: CalendarDay,
    today: LocalDate,
    selection: DateSelection,
    selectionColor: Color,
    continuousSelectionColor: Color,
    textColor: (Color) -> Unit,
): Modifier = composed {
    val (startDate, endDate) = selection
    val padding = 4.dp

    val dayColor = when(day.date.dayOfWeek.value){
        7 ->  Color.Red
        6 ->  Color.Blue
        else ->Color.Black
    }
    when (day.position) {
        DayPosition.MonthDate -> {
            when {
                day.date < today -> {
                    textColor(dayColor.copy(alpha = 0.3f))
                    this
                }

                startDate == day.date && endDate == null -> { // 시작날짜 하나만 선택했을 때 "시작날짜" 디자인
                    textColor(Color.White)
                    padding(padding)
                        .background(color = selectionColor, shape = CircleShape)
                }

                day.date == startDate -> {  // 시작-종료날짜 둘다 선택했을 때 "시작날짜" 디자인
                    textColor(Color.White)
                    padding(vertical = padding)
                        .background(
                            color = continuousSelectionColor,
                            shape = HalfSizeShape(clipStart = true),
                        )
                        .padding(horizontal = padding)
                        .background(color = selectionColor, shape = CircleShape)
                }

                startDate != null && endDate != null && (day.date > startDate && day.date < endDate) -> { // 시작-종료날짜 둘다 선택했을 때 "그 사이 날짜" 디자인
                    textColor(dayColor)
                    padding(vertical = padding)
                        .background(color = continuousSelectionColor)
                }

                day.date == endDate -> { // 시작-종료날짜 둘다 선택했을 때 "종료날짜" 디자인
                    textColor(Color.White)
                    padding(vertical = padding)
                        .background(
                            color = continuousSelectionColor,
                            shape = HalfSizeShape(clipStart = false),
                        )
                        .padding(horizontal = padding)
                        .background(color = selectionColor, shape = CircleShape)
                }

                day.date == today -> { // "오늘날짜" 표시
                    textColor(dayColor)
                    padding(padding)
                        .border(
                            width = 1.dp,
                            shape = CircleShape,
                            color = Color.Blue,
                        )
                }

                else -> {   // "오늘 이후 날짜" 표시
                    textColor(dayColor)
                    this
                }
            }
        }

        DayPosition.InDate -> { // "1일 이전(전월) 날짜" 표시
            textColor(Color.Transparent)  // 안보이게
            if (startDate != null && endDate != null &&
                isInDateBetweenSelection(day.date, startDate, endDate)
            ) {
                padding(vertical = padding)
                    .background(color = continuousSelectionColor)
            } else {
                this
            }
        }
        DayPosition.OutDate -> { // "월 마지막날 이후(익월) 날짜" 표시
            textColor(Color.Transparent)
            if (startDate != null &&
                endDate != null &&
                isOutDateBetweenSelection(day.date, startDate, endDate)
            ) {
                padding(vertical = padding)
                    .background(color = continuousSelectionColor)
            } else {
                this
            }
        }
    }
}




@Composable
fun makeSnackBar(text:String, closeClick:()->Unit){
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight( ),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),

                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(8.dp).height(50.dp),
                contentColor = Color.White
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    IconButton(onClick = closeClick) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun makeDialog(rstMap : MutableMap<String, String?>, onClose:()-> Unit){
    val title = rstMap["rstTitle"]?:""
    val message = rstMap["rstMessage"]?:""
    val footer = if(rstMap["rstCode"]=="1") "※자료출처: 해양조사원 스마트 조석예보, " else rstMap["errorMessage"]?:""
    if(title.isEmpty()) onClose()
    Dialog(
        onDismissRequest = {  },
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp).defaultMinSize(minWidth = 300.dp)) {

                Text(text = title,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xff9a4bf1))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = footer, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        modifier = Modifier.padding(horizontal = 5.dp),
                        onClick = onClose)
                    { Text("확인") }
                }
            }
        }
    }
}


// regionListView : 주먹구구식 추가 기능이라 코딩구조가 조화롭지 않음
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun regionListView(
    map : Map<String, ArrayList<String>>,
    selectedRegionList2 : MutableList<String>,
    onClose:(selectedRegionList : MutableList<String>)-> Unit){
    var selectedRegionList by remember { mutableStateOf( selectedRegionList2  ) }
    val selectedChoSeong = remember { mutableStateOf("")  }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = {  },
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 400.dp)
                    .defaultMinSize(minWidth = 500.dp, minHeight = 100.dp),
            ) {
                FlowRow(Modifier.heightIn(max=100.dp).
                    verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.Center) {
                    selectedRegionList.forEach {
                        Button(
                            modifier = Modifier.height(40.dp).padding(5.dp),
                            onClick = {
                                val tList = selectedRegionList.toMutableList()
                                if (it in selectedRegionList) tList.remove(it)
                                selectedRegionList = tList.toMutableList()
                            },
                            colors = ButtonDefaults.buttonColors(Color(80, 21, 175))
                        ) { Text(text = it, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
                Divider(thickness = 3.dp)

                Box {
                    Column {
                        FlowRow(maxItemsInEachRow = 4, verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.Start) {
                            map.keys.forEach {
                                AnimatedVisibility(selectedChoSeong.value == "") {
                                    Button(
                                        modifier = Modifier.height(40.dp).fillMaxWidth(0.24f).padding(5.dp),
                                        onClick = { selectedChoSeong.value = it },
                                        colors = ButtonDefaults.buttonColors(Color(80, 121, 75)),
                                    ) { Text(text = it, color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                            AnimatedVisibility(selectedChoSeong.value == "") {
                                Button(
                                    modifier = Modifier.height(40.dp).fillMaxWidth(0.24f).padding(5.dp),
                                    onClick = {
                                        onClose(selectedRegionList)
                                        JoSeockXLS().saveRegion(selectedRegionList)
                                              },
                                    colors = ButtonDefaults.buttonColors(Color(10,21,75)),
                                ) {
                                    Text(text = "저장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Column {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                            maxItemsInEachRow = 4, horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            map[selectedChoSeong.value]?.forEach {
                                if (it in selectedRegionList) return@forEach
                                AnimatedVisibility(selectedChoSeong.value != "") {
                                    Button(
                                        modifier = Modifier.height(40.dp).wrapContentWidth().padding(5.dp),
                                        onClick = {
                                            val tList = selectedRegionList.toMutableList()
                                            if (it in selectedRegionList) tList.remove(it)
                                            else if(selectedRegionList.size < 6) tList.add(it)
                                            selectedRegionList = tList.toMutableList()
                                        },
                                        colors = if (it in selectedRegionList) ButtonDefaults.buttonColors(Color(80, 21, 175))
                                                 else ButtonDefaults.buttonColors(Color(228,228,244,)),
                                    ) { Text(text = it, color = Color.Black, fontWeight = FontWeight.Bold) }

                                }
                            }
                            AnimatedVisibility(selectedChoSeong.value != "") {
                                Button(
                                    modifier = Modifier.height(40.dp).wrapContentWidth().padding(5.dp),
                                    onClick = { selectedChoSeong.value = "" },
                                    colors = ButtonDefaults.buttonColors(Color(10,21,75)),
                                ) { Text(text = "확인", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                } // box

            }
        } //surface
    }
}
