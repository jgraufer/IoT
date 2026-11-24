import type { ReactNode } from 'react';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import SensorsIcon from '@mui/icons-material/Sensors';
import PowerIcon from '@mui/icons-material/Power';
import DevicesOtherIcon from '@mui/icons-material/DevicesOther';
import type { DeviceType } from '../types/api';

export const TYPE_ICON: Record<DeviceType, ReactNode> = {
  LIGHT: <LightbulbIcon />,
  THERMOSTAT: <ThermostatIcon />,
  SENSOR: <SensorsIcon />,
  OUTLET: <PowerIcon />,
  OTHER: <DevicesOtherIcon />,
};
