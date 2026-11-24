import { FormControl, InputLabel, MenuItem, Select, Typography } from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { useEdges } from '../session/EdgeProvider';
import { localEdge } from '@target';

export function EdgeSelector() {
  const { edges, edgesLoading, activeEdgeId, activeEdgeName, setActiveEdgeId } = useEdges();

  if (localEdge) {
    return <Typography variant="subtitle1">{activeEdgeName ?? 'Edge no disponible'}</Typography>;
  }

  if (edgesLoading) {
    return (
      <Typography variant="body2" color="text.secondary">
        Cargando casas…
      </Typography>
    );
  }

  if (edges.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Sin casas
      </Typography>
    );
  }

  function handleChange(event: SelectChangeEvent) {
    setActiveEdgeId(event.target.value);
  }

  return (
    <FormControl size="small" sx={{ minWidth: 200 }}>
      <InputLabel id="edge-selector-label">Casa</InputLabel>
      <Select
        labelId="edge-selector-label"
        label="Casa"
        value={activeEdgeId ?? ''}
        onChange={handleChange}
      >
        {edges.map((edge) => (
          <MenuItem key={edge.edgeId} value={edge.edgeId}>
            {edge.name} ({edge.role})
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}
