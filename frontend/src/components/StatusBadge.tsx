interface Props {
    status: string;
}

const STATUS_CONFIG: Record<string, { label: string; classes: string }> = {
    PENDING: { label: 'Pendiente', classes: 'bg-yellow-100 text-yellow-700' },
    SENT: { label: 'Enviado', classes: 'bg-blue-100 text-blue-700' },
    IN_SIGNING: { label: 'Firmando', classes: 'bg-purple-100 text-purple-700' },
    SIGNED: { label: 'Firmado', classes: 'bg-green-100 text-green-700' },
    REJECTED: { label: 'Rechazado', classes: 'bg-red-100 text-red-700' },
    EXPIRED: { label: 'Expirado', classes: 'bg-gray-100 text-gray-500' },
    CANCELLED: { label: 'Cancelado', classes: 'bg-red-50 text-red-400' },
    ARCHIVED: { label: 'Archivado', classes: 'bg-teal-100 text-teal-700' },
};

export default function StatusBadge({ status }: Props) {
    const config = STATUS_CONFIG[status] ?? {
        label: status,
        classes: 'bg-gray-100 text-gray-600',
    };

    return (
        <span className={`px-2 py-1 rounded-full text-xs font-medium ${config.classes}`}>
            {config.label}
        </span>
    );
}