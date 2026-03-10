import * as echarts from "echarts/core";
import { BarChart, LineChart, PieChart, ScatterChart } from "echarts/charts";
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent
} from "echarts/components";
import { LabelLayout, UniversalTransition } from "echarts/features";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  DatasetComponent,
  TransformComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  BarChart,
  LineChart,
  PieChart,
  ScatterChart,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer
]);

export default echarts;
